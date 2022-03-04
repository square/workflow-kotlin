package com.squareup.workflow1.ui.container

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import androidx.transition.Scene
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedRendering
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.canShowRendering
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.container.BackStackConfig.First
import com.squareup.workflow1.ui.container.BackStackConfig.Other
import com.squareup.workflow1.ui.container.ViewStateCache.SavedState
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
  private var currentRendering: BackStackScreen<NamedRendering<*>>? = null

  public fun update(
    newRendering: BackStackScreen<*>,
    newViewEnvironment: ViewEnvironment
  ) {
    val config = if (newRendering.backStack.isEmpty()) First else Other
    val environment = newViewEnvironment + config

    val named: BackStackScreen<NamedRendering<*>> = newRendering
      // ViewStateCache requires that everything be Named.
      // It's fine if client code is already using Named for its own purposes, recursion works.
      .map { NamedRendering(it, "backstack") }

    val oldViewMaybe = currentView

    // If existing view is compatible, just update it.
    oldViewMaybe
      ?.takeIf { it.canShowRendering(named.top) }
      ?.let {
        viewStateCache.prune(named.frames)
        it.showRendering(named.top, environment)
        return
      }

    val newView = named.top.buildView(
      viewEnvironment = environment,
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
   * Note that views are showing renderings of type [NamedRendering]`<BackStackScreen<*>>`.
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
    return SavedState(super.onSaveInstanceState(), viewStateCache)
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? SavedState)
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
    val parentRegistryOwner = stateRegistryOwnerFromViewTreeOrContext(this)
    val key = Compatible.keyFor(this.getRendering()!!)
    viewStateCache.attachToParentRegistryOwner(key, parentRegistryOwner)
  }

  override fun onDetachedFromWindow() {
    // Disconnect our state cache from our parent SavedStateRegistry so that it doesn't get asked
    // to save state anymore.
    viewStateCache.detachFromParentRegistry()
    super.onDetachedFromWindow()
  }
}
