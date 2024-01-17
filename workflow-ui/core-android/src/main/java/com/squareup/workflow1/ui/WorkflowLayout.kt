package com.squareup.workflow1.ui

import android.content.Context
import android.os.Build.VERSION
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.squareup.workflow1.ui.androidx.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.onBackPressedDispatcherOwnerOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A view that can be driven by a stream of [Screen] renderings passed to its [take] method.
 *
 * Suitable for use as the content view of an [Activity][android.app.Activity.setContentView],
 * or [Fragment][androidx.fragment.app.Fragment.onCreateView].
 *
 * [id][setId] defaults to [R.id.workflow_layout], as a convenience to ensure that
 * view persistence will work without requiring authors to be immersed in Android arcana.
 *
 * See [com.squareup.workflow1.ui.renderWorkflowIn] for typical use
 * with a [com.squareup.workflow1.Workflow].
 */
@WorkflowUiExperimentalApi
public class WorkflowLayout(
  context: Context,
  attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {
  init {
    if (id == NO_ID) id = R.id.workflow_layout
  }

  private val showing: WorkflowViewStub = WorkflowViewStub(context).also { rootStub ->
    rootStub.updatesVisibility = false
    rootStub.propagatesLayoutParams = false
    addView(rootStub)
  }

  private var restoredChildState: SparseArray<Parcelable>? = null

  /**
   * Calls [WorkflowViewStub.show] on the [WorkflowViewStub] that is the only
   * child of this view.
   *
   * It's more common for a `Workflow`-based `Activity` or `Fragment` to use
   * [take] than to call this method directly. It is exposed to allow clients to
   * make their own choices about how exactly to consume a stream of renderings.
   */
  public fun show(
    rootScreen: Screen,
    environment: ViewEnvironment = ViewEnvironment.EMPTY
  ) {
    showing.show(rootScreen, environment.withOnBackDispatcher())
    restoredChildState?.let { restoredState ->
      restoredChildState = null
      showing.actual.restoreHierarchyState(restoredState)
    }
  }

  /**
   * This is the most common way to bootstrap a [Workflow][com.squareup.workflow1.Workflow]
   * driven UI. Collects [renderings] and calls [show] with each one.
   *
   * To configure a root [ViewEnvironment], use
   * [EnvironmentScreen][com.squareup.workflow1.ui.EnvironmentScreen] as your
   * root rendering type, perhaps via
   * [withEnvironment][com.squareup.workflow1.ui.withEnvironment] or
   * [withRegistry][com.squareup.workflow1.ui.withRegistry].
   *
   * @param [lifecycle] the lifecycle that defines when and how this view should be updated.
   * Typically this comes from `ComponentActivity.lifecycle` or  `Fragment.lifecycle`.
   * @param [repeatOnLifecycle] the lifecycle state in which renderings should be actively
   * updated. Defaults to STARTED, which is appropriate for Activity and Fragment.
   * @param [collectionContext] additional [CoroutineContext] we want for the coroutine that is
   * launched to collect the renderings. This should not override the [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher]
   * but may include some other instrumentation elements.
   */
  @OptIn(ExperimentalStdlibApi::class)
  public fun take(
    lifecycle: Lifecycle,
    renderings: Flow<Screen>,
    repeatOnLifecycle: State = STARTED,
    collectionContext: CoroutineContext = EmptyCoroutineContext
  ) {
    // We remove the dispatcher as we want to use what is provided by the lifecycle.coroutineScope.
    val contextWithoutDispatcher = collectionContext.minusKey(CoroutineDispatcher.Key)
    val lifecycleDispatcher = lifecycle.coroutineScope.coroutineContext[CoroutineDispatcher.Key]
    // Just like https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
    lifecycle.coroutineScope.launch(contextWithoutDispatcher) {
      lifecycle.repeatOnLifecycle(repeatOnLifecycle) {
        require(coroutineContext[CoroutineDispatcher.Key] == lifecycleDispatcher) {
          "Collection dispatch should happen on the lifecycle's dispatcher."
        }
        renderings.collect { show(it) }
      }
    }
  }

  @Deprecated(
    "Use show",
    ReplaceWith(
      "show(asScreen(newRendering).withEnvironment(environment))",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.withEnvironment",
    )
  )
  public fun update(
    newRendering: Any,
    environment: ViewEnvironment
  ) {
    @Suppress("DEPRECATION")
    showing.update(newRendering, environment.withOnBackDispatcher())
    restoredChildState?.let { restoredState ->
      restoredChildState = null
      showing.actual.restoreHierarchyState(restoredState)
    }
  }

  @Deprecated(
    "Use take()",
    ReplaceWith(
      "take(lifecycle, " +
        "renderings.map { asScreen(it).withEnvironment(environment) }, " +
        "repeatOnLifecycle)",
      "com.squareup.workflow1.ui.ViewEnvironment",
      "com.squareup.workflow1.ui.ViewRegistry",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.withEnvironment",
      "kotlinx.coroutines.flow.map"
    )
  )
  @Suppress("DEPRECATION")
  public fun start(
    lifecycle: Lifecycle,
    renderings: Flow<Any>,
    repeatOnLifecycle: State = STARTED,
    environment: ViewEnvironment = ViewEnvironment.EMPTY
  ) {
    // Just like https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(repeatOnLifecycle) {
        renderings.collect { update(it, environment) }
      }
    }
  }

  @Deprecated(
    "Use take()",
    ReplaceWith(
      "take(lifecycle, renderings.map { asScreen(it).withRegistry(registry) })",
      "com.squareup.workflow1.ui.ViewEnvironment",
      "com.squareup.workflow1.ui.ViewRegistry",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.withRegistry",
      "kotlinx.coroutines.flow.map"
    )
  )
  public fun start(
    lifecycle: Lifecycle,
    renderings: Flow<Any>,
    registry: ViewRegistry
  ) {
    @Suppress("DEPRECATION")
    start(
      lifecycle = lifecycle,
      renderings = renderings,
      environment = ViewEnvironment(mapOf(ViewRegistry to registry))
    )
  }

  @Deprecated(
    "Use take()",
    ReplaceWith(
      "take(lifecycle, renderings.map { asScreen(it).withEnvironment(environment) })",
      "com.squareup.workflow1.ui.ViewEnvironment",
      "com.squareup.workflow1.ui.ViewRegistry",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.withEnvironment",
      "kotlinx.coroutines.flow.map"
    )
  )
  @Suppress("DEPRECATION")
  public fun start(
    renderings: Flow<Any>,
    environment: ViewEnvironment = ViewEnvironment.EMPTY
  ) {
    takeWhileAttached(renderings) { update(it, environment) }
  }

  @Deprecated(
    "Use take()",
    ReplaceWith(
      "take(lifecycle, renderings.map { asScreen(it).withRegistry(registry) })",
      "com.squareup.workflow1.ui.ViewEnvironment",
      "com.squareup.workflow1.ui.ViewRegistry",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.withRegistry",
      "kotlinx.coroutines.flow.map"
    )
  )
  public fun start(
    renderings: Flow<Any>,
    registry: ViewRegistry
  ) {
    @Suppress("DEPRECATION")
    start(renderings, ViewEnvironment(mapOf(ViewRegistry to registry)))
  }

  override fun onSaveInstanceState(): Parcelable {
    return SavedState(
      super.onSaveInstanceState()!!,
      SparseArray<Parcelable>().also { array -> showing.actual.saveHierarchyState(array) }
    )
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    (state as? SavedState)
      ?.let {
        restoredChildState = it.childState
        super.onRestoreInstanceState(state.superState)
      }
      ?: super.onRestoreInstanceState(super.onSaveInstanceState())
    // ?: Some other class wrote state, but we're not allowed to skip the call to super.
    // Make a no-op call.
  }

  /**
   * Attempts to seed the [ViewEnvironment] with an [OnBackPressedDispatcherOwnerKey]
   * value if one wasn't set already. We're priming the pump that our
   * `ViewEnvironment.onBackPressedDispatcherOwner` call relies on.
   */
  private fun ViewEnvironment.withOnBackDispatcher(): ViewEnvironment {
    val envWithOnBack = if (map.containsKey(OnBackPressedDispatcherOwnerKey)) {
      this
    } else {
      this@WorkflowLayout.onBackPressedDispatcherOwnerOrNull()
        ?.let { this@withOnBackDispatcher + (OnBackPressedDispatcherOwnerKey to it) }
        ?: this
    }
    return envWithOnBack
  }

  private class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable?,
      childState: SparseArray<Parcelable>
    ) : super(superState) {
      this.childState = childState
    }

    constructor(source: Parcel) : super(source) {
      this.childState = if (VERSION.SDK_INT >= 33) {
        source.readSparseArray(SavedState::class.java.classLoader, Parcelable::class.java)!!
      } else {
        @Suppress("DEPRECATION")
        source.readSparseArray(SavedState::class.java.classLoader)!!
      }
    }

    val childState: SparseArray<Parcelable>

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      @Suppress("UNCHECKED_CAST")
      out.writeSparseArray(childState as SparseArray<Any>)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }

  /**
   * Subscribes [update] to [source] only while this [View] is attached to a window.
   * Deprecated, leads to redundant calls to OnAttachStateChangeListener.onViewAttachedToWindow.
   * To be deleted along with its callers.
   */
  @Deprecated("Do not use.")
  private fun <S : Any> View.takeWhileAttached(
    source: Flow<S>,
    update: (S) -> Unit
  ) {
    val listener = object : OnAttachStateChangeListener {
      val scope = CoroutineScope(Dispatchers.Main.immediate)
      var job: Job? = null

      override fun onViewAttachedToWindow(v: View) {
        job = source.onEach { screen -> update(screen) }
          .launchIn(scope)
      }

      override fun onViewDetachedFromWindow(v: View) {
        job?.cancel()
        job = null
      }
    }

    this.addOnAttachStateChangeListener(listener)
  }
}
