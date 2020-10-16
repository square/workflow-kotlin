package com.squareup.workflow1.ui

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.transition.Scene
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.squareup.workflow1.ui.BackStackConfig.First
import com.squareup.workflow1.ui.BackStackConfig.Other

/**
 * A container view that can display a stream of [BackStackViewRendering] instances.
 */
@WorkflowUiExperimentalApi
open class BackStackView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {

  private val viewStateCache = ViewStateCache()

  private val currentView: View? get() = if (childCount > 0) getChildAt(0) else null
  private var currentRendering: BackStackViewRendering<NamedViewRendering>? = null

  protected fun update(
    newRendering: BackStackViewRendering<*>,
    newViewEnvironment: ViewEnvironment
  ) {
    val config = if (newRendering.backStack.isEmpty()) First else Other
    val environment = newViewEnvironment + (BackStackConfig to config)

    val named: BackStackViewRendering<NamedViewRendering> = newRendering
        // ViewStateCache requires that everything be Named.
        // It's fine if client code is already using Named for its own purposes, recursion works.
        .map { NamedViewRendering(it, "backstack") }

    val oldViewMaybe = currentView

    // If existing view is compatible, just update it.
    oldViewMaybe
        ?.takeIf { it.canShowRendering(named.top) }
        ?.let {
          viewStateCache.prune(named.frames)
          it.showRendering(named.top, environment)
          return
        }

    val newView = named.top.buildView(environment, this)
    viewStateCache.update(named.backStack, oldViewMaybe, newView)

    val popped = currentRendering?.backStack?.any { compatible(it, named.top) } == true

    performTransition(oldViewMaybe, newView, popped)
    currentRendering = named
  }

  /**
   * Called from [View.showRendering] to swap between views.
   * Subclasses can override to customize visual effects. There is no need to call super.
   * Note that views are showing renderings of type [NamedViewRendering]`<BackStackViewRendering<*>>`.
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
          val oldBody: View? = oldView.findViewById(R.id.back_stack_view_body)
          val newBody: View? = newView.findViewById(R.id.back_stack_view_body)

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
        ?: super.onRestoreInstanceState(state)
  }

  companion object : ViewBuilder<BackStackViewRendering<*>>
  by BespokeViewBuilder(
      type = BackStackViewRendering::class,
      constructor = { initialRendering, initialEnv, context, _ ->
        BackStackView(context)
            .apply {
              id = R.id.back_stack_view
              layoutParams = (ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
              bindShowRendering(initialRendering, initialEnv, ::update)
            }
      }
  )
}
