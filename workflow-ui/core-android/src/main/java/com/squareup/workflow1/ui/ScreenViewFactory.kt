package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndModalsContainer
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

/**
 * Factory for [View] instances that can show renderings of type [RenderingT] : [Screen].
 *
 * Two concrete [ScreenViewFactory] implementations are provided:
 *
 *  - The various [bind][ScreenViewRunner.bind] methods on [ScreenViewRunner] allow easy use of
 *    Android XML layout resources and [AndroidX ViewBinding][androidx.viewbinding.ViewBinding].
 *
 *  - [ManualScreenViewFactory] allows views to be built from code.
 *
 * It's simplest to have your rendering classes implement [AndroidScreen] to associate
 * them with appropriate an appropriate [ScreenViewFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in RenderingT : Screen> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a View ready to display [initialRendering] (and any succeeding values)
   * via [View.showRendering].
   */
  public fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeScreenViewFactory]
 * than to call this method directly.
 *
 * Finds a [ScreenViewFactory] to create a [View] to display [this@buildView]. The new view
 * can be updated via calls to [View.showRendering] -- that is, it is guaranteed that
 * [bindShowRendering] has been called on this view.
 *
 * The returned view will have a
 * [WorkflowLifecycleOwner][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner]
 * set on it. The returned view must EITHER:
 *
 * 1. Be attached at least once to ensure that the lifecycle eventually gets destroyed (because its
 *    parent is destroyed), or
 * 2. Have its
 *    [WorkflowLifecycleOwner.destroyOnDetach][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.destroyOnDetach]
 *    called, which will either schedule the
 *    lifecycle to be destroyed if the view is attached, or destroy it immediately if it's detached.
 *
 * [WorkflowViewStub] takes care of this chore itself.
 *
 * @param initializeView Optional function invoked immediately after the [View] is
 * created (that is, immediately after the call to [ScreenViewFactory.buildView]).
 * [showRendering], [getRendering] and [environment] are all available when this is called.
 * Defaults to a call to [View.showFirstRendering].
 *
 * @throws IllegalArgumentException if no builder can be find for type [ScreenT]
 *
 * @throws IllegalStateException if the matching [ScreenViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
  viewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  initializeView: View.() -> Unit = { showFirstRendering() }
): View {
  val viewFactory = viewEnvironment.getViewFactoryForRendering(this)

  return viewFactory.buildView(this, viewEnvironment, contextForNewView, container).also { view ->
    checkNotNull(view.showRenderingTag) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ScreenViewFactory::class.java.name} that created it."
    }
    initializeView.invoke(view)
  }
}

/**
 * Default implementation for the `initializeView` argument of [Screen.buildView],
 * and for [DecorativeScreenViewFactory.initializeView]. Calls [showRendering] against
 * [getRendering] and [environment].
 */
@WorkflowUiExperimentalApi
public fun View.showFirstRendering() {
  showRendering(getRendering()!!, environment!!)
}

@WorkflowUiExperimentalApi
internal fun <ScreenT : Screen>
  ViewEnvironment.getViewFactoryForRendering(rendering: ScreenT): ScreenViewFactory<ScreenT> {
  val entry = get(ViewRegistry).getEntryFor(rendering::class)

  @Suppress("UNCHECKED_CAST", "DEPRECATION")
  return (entry as? ScreenViewFactory<ScreenT>)
    ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
    ?: (rendering as? AsScreen<*>)?.let { AsScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? BackStackScreen<*>)?.let {
      BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? BodyAndModalsScreen<*, *>)?.let {
      BodyAndModalsContainer as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? NamedScreen<*>)?.let { NamedScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? EnvironmentScreen<*>)?.let {
      EnvironmentScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: throw IllegalArgumentException(
      "A ScreenViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidScreen. Instead found $entry."
    )
}
