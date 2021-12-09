package com.squareup.workflow1.ui.backstack

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import androidx.transition.Scene
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.backstack.BackStackConfig.First
import com.squareup.workflow1.ui.backstack.BackStackConfig.Other
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.canShowRendering
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.container.R
import com.squareup.workflow1.ui.getRendering
import com.squareup.workflow1.ui.showRendering
import com.squareup.workflow1.ui.start

/**
 * A container view that can display a stream of [BackStackScreen] instances.
 *
 * This container supports saving and restoring the view state of each of its subviews corresponding
 * to the renderings in its [BackStackScreen]. It supports two distinct state mechanisms:
 *  1. Classic view hierarchy state ([View.onSaveInstanceState]/[View.onRestoreInstanceState])
 *  2. AndroidX [SavedStateRegistry] via [ViewTreeSavedStateRegistryOwner].
 *
 * ## A note about `SavedStateRegistry` support.
 *
 * The [SavedStateRegistry] API involves defining string keys to associate with state bundles. These
 * keys must be unique relative to the instance of the registry they are saved in. To support this
 * requirement, [BackStackContainer] tries to generate a best-effort unique key by combining its
 * fully-qualified class name with both its [view ID][View.getId] and the
 * [compatibility key][com.squareup.workflow1.ui.Compatible.compatibilityKey] of its rendering.
 * This method isn't guaranteed to give a unique registry key, but it should be good enough: If you
 * need to nest multiple [BackStackContainer]s under the same `SavedStateRegistry`, just wrap each
 * [BackStackScreen] with a [Named], or give each [BackStackContainer] a unique view ID.
 *
 * There's a potential issue here where if our ID is changed to something else, then another
 * [BackStackContainer] is added with our old ID, that container will overwrite our state. Since
 * they'd both be using the same key, [SavedStateRegistry] would throw an exception. As long as this
 * container is detached before its ID is changed, it shouldn't be a problem.
 */
@WorkflowUiExperimentalApi
public open class BackStackContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {

  private val viewStateCache = ViewStateCache()

  private val currentView: View? get() = if (childCount > 0) getChildAt(0) else null
  private var currentRendering: BackStackScreen<Named<*>>? = null
  private var stateRegistryKey: String? = null

  protected fun update(
    newRendering: BackStackScreen<*>,
    newViewEnvironment: ViewEnvironment
  ) {
    updateStateRegistryKey(newViewEnvironment)

    val config = if (newRendering.backStack.isEmpty()) First else Other
    val environment = newViewEnvironment + (BackStackConfig to config)

    val named: BackStackScreen<Named<*>> = newRendering
      // ViewStateCache requires that everything be Named.
      // It's fine if client code is already using Named for its own purposes, recursion works.
      .map { Named(it, "backstack") }

    val oldViewMaybe = currentView

    // If existing view is compatible, just update it.
    oldViewMaybe
      ?.takeIf { it.canShowRendering(named.top) }
      ?.let {
        viewStateCache.prune(named.frames)
        it.showRendering(named.top, environment)
        return
      }

    val newView = environment[ViewRegistry].buildView(
      initialRendering = named.top,
      initialViewEnvironment = environment,
      contextForNewView = this.context,
      container = this,
      viewStarter = { view, doStart ->
        WorkflowLifecycleOwner.installOn(view)
        doStart()
      }
    )
    newView.start()
    viewStateCache.update(named.backStack, oldViewMaybe, newView)

    val popped = currentRendering?.backStack?.any { compatible(it, named.top) } == true

    performTransition(oldViewMaybe, newView, popped)
    // Notify the view we're about to replace that it's going away.
    oldViewMaybe?.let(WorkflowLifecycleOwner::get)?.destroyOnDetach()

    currentRendering = named
  }

  /**
   * Called from [View.showRendering] to swap between views.
   * Subclasses can override to customize visual effects. There is no need to call super.
   * Note that views are showing renderings of type [Named]`<BackStackScreen<*>>`.
   *
   * @param oldViewMaybe the outgoing view, or null if this is the initial rendering.
   * @param newView the view that should replace [oldViewMaybe] (if it exists), and become
   * this view's only child
   * @param popped true if we should give the appearance of popping "back" to a previous rendering,
   * false if a new rendering is being "pushed". Should be ignored if [oldViewMaybe] is null.
   */
  protected open fun performTransition(
    oldViewMaybe: View?,
    newView: View,
    popped: Boolean
  ) {
    // Showing something already, transition with push or pop effect.
    oldViewMaybe
      ?.let { oldView ->
        val oldBody: View? = oldView.findViewById(R.id.back_stack_body)
        val newBody: View? = newView.findViewById(R.id.back_stack_body)

        val oldTarget: View
        val newTarget: View
        if (oldBody != null && newBody != null) {
          oldTarget = oldBody
          newTarget = newBody
        } else {
          oldTarget = oldView
          newTarget = newView
        }

        val (outEdge, inEdge) = when (popped) {
          false -> Gravity.START to Gravity.END
          true -> Gravity.END to Gravity.START
        }

        val transition = TransitionSet()
          .addTransition(Slide(outEdge).addTarget(oldTarget))
          .addTransition(Slide(inEdge).addTarget(newTarget))
          .setInterpolator(AccelerateDecelerateInterpolator())

        TransitionManager.go(Scene(this, newView), transition)
        return
      }

    // This is the first view, just show it.
    addView(newView)
  }

  override fun onSaveInstanceState(): Parcelable {
    return ViewStateCache.SavedState(super.onSaveInstanceState(), viewStateCache)
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? ViewStateCache.SavedState)
      ?.let {
        viewStateCache.restore(it.viewStateCache)
        super.onRestoreInstanceState(state.superState)
      }
      ?: super.onRestoreInstanceState(super.onSaveInstanceState())
    // Some other class wrote state, but we're not allowed to skip
    // the call to super. Make a no-op call.
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    // Wire up our viewStateCache to our parent SavedStateRegistry.
    val parentRegistryOwner = stateRegistryOwnerFromViewTreeOrContext(this)!!
    val key = checkNotNull(stateRegistryKey) {
      "Expected stateRegistryKey to have been set – the view seems to be getting attached before " +
        "its first update: $this"
    }

    viewStateCache.attachToParentRegistry(key, parentRegistryOwner)
  }

  override fun onDetachedFromWindow() {
    // Disconnect our state cache from our parent SavedStateRegistry so that it doesn't get asked
    // to save state anymore.
    viewStateCache.detachFromParentRegistry()
    super.onDetachedFromWindow()
  }

  /**
   * See the note about SavedStateRegistry support in this class's kdoc for some caveats.
   */
  private fun getStateRegistryKey(): String {
    val namedKeyOrNull = run {
      val rendering = getRendering<Any>() as? Named<*>
      rendering?.compatibilityKey
    }
    val nameSuffix = namedKeyOrNull?.let { "-$it" } ?: ""
    val idSuffix = if (id == NO_ID) "" else "-$id"
    return BackStackContainer::class.java.name + nameSuffix + idSuffix
  }

  /**
   * In order to save our state with a unique ID in our parent's registry, we use a combination
   * of this class name, our [compatibility key][Named.compatibilityKey] if specified, and our view
   * ID if specified. This method isn't guaranteed to give a unique registry key, but it should be
   * good enough: If you need to nest multiple [BackStackContainer]s under the same
   * `SavedStateRegistry`, just wrap each [BackStackScreen] with a [Named], or give each
   * [BackStackContainer] a unique view ID.
   *
   * There's a potential issue here where if our ID is changed to something else, then another
   * BackStackContainer is added with our old ID, that container will overwrite our state. Since
   * they'd both be using the same key, SavedStateRegistry would throw an exception. That's a
   * pretty unlikely situation though I think. And as long as this container is detached before
   * its ID is changed, it won't be a problem.
   */
  private fun updateStateRegistryKey(environment: ViewEnvironment) {
    val idSuffix = if (id == NO_ID) "" else "-$id"
    val keyPrefix = environment.getBackStackStateKeyPrefix
    val newKey = keyPrefix + BackStackContainer::class.java.name + idSuffix

    if (stateRegistryKey != null && stateRegistryKey != newKey) {
      Log.wtf(
        "workflow1",
        "BackStackContainer state registry key changed – view state may be lost:" +
          " from $stateRegistryKey to $newKey"
      )
    }
    stateRegistryKey = newKey
  }

  public companion object : ViewFactory<BackStackScreen<*>>
  by BuilderViewFactory(
    type = BackStackScreen::class,
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      BackStackContainer(context)
        .apply {
          id = R.id.workflow_back_stack_container
          layoutParams = (ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
          bindShowRendering(initialRendering, initialEnv, ::update)
        }
    }
  )
}
