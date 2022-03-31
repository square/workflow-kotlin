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
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.canShow
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.container.BackStackConfig.First
import com.squareup.workflow1.ui.container.BackStackConfig.Other
import com.squareup.workflow1.ui.container.ViewStateCache.SavedState
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory

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

  private var currentViewHolder: ScreenViewHolder<NamedScreen<*>>? = null
  private var currentRendering: BackStackScreen<NamedScreen<*>>? = null

  /**
   * Unique identifier for this view for SavedStateRegistry purposes. Based on the
   * [Compatible.keyFor] the current rendering. Taking this approach allows
   * feature developers to take control over naming, e.g. by wrapping renderings
   * with [NamedScreen][com.squareup.workflow1.ui.NamedScreen].
   */
  private lateinit var savedStateParentKey: String

  public fun update(
    newRendering: BackStackScreen<*>,
    newViewEnvironment: ViewEnvironment
  ) {
    savedStateParentKey = keyFor(newViewEnvironment[ScreenViewHolder.Showing])

    val config = if (newRendering.backStack.isEmpty()) First else Other
    val environment = newViewEnvironment + config

    val named: BackStackScreen<NamedScreen<*>> = newRendering
      // ViewStateCache requires that everything be Named.
      // It's fine if client code is already using Named for its own purposes, recursion works.
      .map { NamedScreen(it, "backstack") }

    val oldViewHolderMaybe = currentViewHolder

    // If existing view is compatible, just update it.
    oldViewHolderMaybe
      ?.takeIf { it.canShow(named.top) }
      ?.let {
        viewStateCache.prune(named.frames)
        it.show(named.top, environment)
        return
      }

    val newViewHolder = named.top.toViewFactory(environment).startShowing(
      initialRendering = named.top,
      initialEnvironment = environment,
      contextForNewView = this.context,
      container = this,
      viewStarter = { view, doStart ->
        WorkflowLifecycleOwner.installOn(view)
        doStart()
      }
    )
    viewStateCache.update(named.backStack, oldViewHolderMaybe, newViewHolder)

    val popped = currentRendering?.backStack?.any { compatible(it, named.top) } == true

    performTransition(oldViewHolderMaybe, newViewHolder, popped)
    // Notify the view we're about to replace that it's going away.
    oldViewHolderMaybe?.view?.let(WorkflowLifecycleOwner::get)?.destroyOnDetach()

    currentViewHolder = newViewHolder
    currentRendering = named
  }

  /**
   * Called from [update] (via [ScreenViewHolder.show] to swap between views. Subclasses
   * can override to customize visual effects. There is no need to call super. Note that
   * views are showing renderings of type [NamedScreen]`<*>`.
   *
   * @param oldHolderMaybe the outgoing view, or null if this is the initial rendering.
   * @param newHolder the view that should replace [oldHolderMaybe] (if it exists), and become
   * this view's only child
   * @param popped true if we should give the appearance of popping "back" to a previous rendering,
   * false if a new rendering is being "pushed". Should be ignored if [oldHolderMaybe] is null.
   */
  protected open fun performTransition(
    oldHolderMaybe: ScreenViewHolder<NamedScreen<*>>?,
    newHolder: ScreenViewHolder<NamedScreen<*>>,
    popped: Boolean
  ) {
    // Showing something already, transition with push or pop effect.
    oldHolderMaybe
      ?.let { oldHolder ->
        val oldBody: View? = oldHolder.view.findViewById(R.id.back_stack_body)
        val newBody: View? = newHolder.view.findViewById(R.id.back_stack_body)

        val oldTarget: View
        val newTarget: View
        if (oldBody != null && newBody != null) {
          oldTarget = oldBody
          newTarget = newBody
        } else {
          oldTarget = oldHolder.view
          newTarget = newHolder.view
        }

        val (outEdge, inEdge) = when (popped) {
          false -> Gravity.START to Gravity.END
          true -> Gravity.END to Gravity.START
        }

        val transition = TransitionSet()
          .addTransition(Slide(outEdge).addTarget(oldTarget))
          .addTransition(Slide(inEdge).addTarget(newTarget))
          .setInterpolator(AccelerateDecelerateInterpolator())

        TransitionManager.endTransitions(this)
        TransitionManager.go(Scene(this, newHolder.view), transition)
        return
      }

    // This is the first view, just show it.
    addView(newHolder.view)
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
    viewStateCache.attachToParentRegistryOwner(savedStateParentKey, parentRegistryOwner)
  }

  override fun onDetachedFromWindow() {
    // Disconnect our state cache from our parent SavedStateRegistry so that it doesn't get asked
    // to save state anymore.
    viewStateCache.detachFromParentRegistry()
    super.onDetachedFromWindow()
  }
}
