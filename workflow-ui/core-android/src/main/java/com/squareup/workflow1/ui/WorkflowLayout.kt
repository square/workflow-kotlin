package com.squareup.workflow1.ui

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.withEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * A view that can be driven by a stream of [Screen] renderings passed to its [take] method.
 * To configure the [ViewEnvironment] in play, use [EnvironmentScreen] as your root rendering type.
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
    addView(rootStub, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
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
  public fun show(rootScreen: Screen) {
    showing.show(rootScreen, rootScreen.withEnvironment().viewEnvironment)
    restoredChildState?.let { restoredState ->
      restoredChildState = null
      showing.actual.restoreHierarchyState(restoredState)
    }
  }

  /**
   * This is the most common way to bootstrap a [Workflow][com.squareup.workflow1.Workflow]
   * driven UI. Collects [renderings] and calls [show] with each one.
   *
   * @param [lifecycle] the lifecycle that defines when and how this view should be updated.
   * Typically this comes from `ComponentActivity.lifecycle` or  `Fragment.lifecycle`.
   */
  public fun take(
    lifecycle: Lifecycle,
    renderings: Flow<Screen>
  ) {
    // Just like https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        renderings.collect { show(it.withEnvironment()) }
      }
    }
  }

  @Deprecated(
    "Use show",
    ReplaceWith(
      "show(asScreen(newRendering).withEnvironment(environment))",
      "com.squareup.workflow1.ui.container.withEnvironment",
      "com.squareup.workflow1.ui.asScreen"
    )
  )
  public fun update(
    newRendering: Any,
    environment: ViewEnvironment
  ) {
    @Suppress("DEPRECATION")
    showing.update(newRendering, environment)
    restoredChildState?.let { restoredState ->
      restoredChildState = null
      showing.actual.restoreHierarchyState(restoredState)
    }
  }

  @Deprecated(
    "Use take()",
    ReplaceWith(
      "take(lifecycle, renderings.map { asScreen(it).withEnvironment(environment) })",
      "com.squareup.workflow1.ui.ViewEnvironment",
      "com.squareup.workflow1.ui.ViewRegistry",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.container.withEnvironment",
      "kotlinx.coroutines.flow.map"
    )
  )
  @Suppress("DEPRECATION")
  public fun start(
    lifecycle: Lifecycle,
    renderings: Flow<Any>,
    environment: ViewEnvironment = ViewEnvironment.EMPTY
  ) {
    // Just like https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
      "com.squareup.workflow1.ui.container.withRegistry",
      "kotlinx.coroutines.flow.map"
    )
  )
  public fun start(
    lifecycle: Lifecycle,
    renderings: Flow<Any>,
    registry: ViewRegistry
  ) {
    @Suppress("DEPRECATION")
    start(lifecycle, renderings, ViewEnvironment(mapOf(ViewRegistry to registry)))
  }

  @Deprecated(
    "Use take()",
    ReplaceWith(
      "take(lifecycle, renderings.map { asScreen(it).withEnvironment(environment) })",
      "com.squareup.workflow1.ui.ViewEnvironment",
      "com.squareup.workflow1.ui.ViewRegistry",
      "com.squareup.workflow1.ui.asScreen",
      "com.squareup.workflow1.ui.container.withEnvironment",
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
      "com.squareup.workflow1.ui.container.withRegistry",
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

  private class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable?,
      childState: SparseArray<Parcelable>
    ) : super(superState) {
      this.childState = childState
    }

    constructor(source: Parcel) : super(source) {
      this.childState = source.readSparseArray(SavedState::class.java.classLoader)!!
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

      override fun onViewAttachedToWindow(v: View?) {
        job = source.onEach { screen -> update(screen) }
          .launchIn(scope)
      }

      override fun onViewDetachedFromWindow(v: View?) {
        job?.cancel()
        job = null
      }
    }

    this.addOnAttachStateChangeListener(listener)
  }
}
