package com.squareup.workflow1.ui.backstack

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.transition.Scene
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackConfig.First
import com.squareup.workflow1.ui.backstack.BackStackConfig.Other
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.canShowRendering
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.showFirstRendering
import com.squareup.workflow1.ui.showRendering

/**
 * A container view that can display a stream of [BackStackScreen] instances.
 */
@WorkflowUiExperimentalApi
public open class BackStackContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {

  private val viewStateCache = ViewStateCache()

  /**
   * We must hold the [Lifecycle]s of all child views in the [INITIALIZED] state until this view has
   * been [restored][onRestoreInstanceState], our [currentView] has had its `SavedStateRegistry`
   * restored. After that, we can allow those lifecycles to catch up to the real one.
   */
  private val lifecycleRatchet = object : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = registry
  }

  private val currentView: View? get() = if (childCount > 0) getChildAt(0) else null
  private var currentRendering: BackStackScreen<Named<*>>? = null

  protected fun update(
    newRendering: BackStackScreen<*>,
    newViewEnvironment: ViewEnvironment
  ) {
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
      initializeView = {
        WorkflowLifecycleOwner.installOn(this, lifecycleRatchet = lifecycleRatchet.lifecycle)
        showFirstRendering()
      }
    )
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
    currentView?.let(viewStateCache::saveCurrentViewStateRegistry)
    return ViewStateCache.SavedState(super.onSaveInstanceState(), viewStateCache)
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? ViewStateCache.SavedState)
      ?.let {
        viewStateCache.restore(it.viewStateCache, currentView)
        // Now that the SavedStateRegistry for the current view has been restored, we can allow its
        // lifecycle to move to the CREATED state (or whatever state we happen to be in by now).
        lifecycleRatchet.registry.currentState = RESUMED
        super.onRestoreInstanceState(state.superState)
      }
      ?: super.onRestoreInstanceState(super.onSaveInstanceState())
    // Some other class wrote state, but we're not allowed to skip
    // the call to super. Make a no-op call.
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
