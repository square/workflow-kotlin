package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.RootScreen
import com.squareup.workflow1.ui.container.RootScreenViewFactory

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeScreenViewFactory]
 * than to call this method directly.
 *
 * Returns the [ScreenViewFactory] that builds [View] instances suitable to display the given [rendering],
 * via subsequent calls to [View.showRendering].
 *
 * Prefers factories found via [ViewRegistry.getEntryFor]. If that returns null, falls
 * back to the builder provided by the rendering's implementation of
 * [AndroidScreen.viewFactory], if there is one. Note that this means that a
 * compile time [AndroidScreen.viewFactory] binding can be overridden at runtime.
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
 * @throws IllegalArgumentException if no builder can be find for type [ScreenT]
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen>
  ViewEnvironment.getViewFactoryForRendering(rendering: ScreenT): ScreenViewFactory<ScreenT> {
  @Suppress("UNCHECKED_CAST", "DEPRECATION")
  return (get(ViewRegistry).getEntryFor(rendering::class) as? ScreenViewFactory<ScreenT>)
    ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
    ?: (rendering as? AsScreen<*>)?.let { AsScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? BackStackScreen<*>)?.let {
      BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? NamedScreen<*>)?.let { NamedScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? RootScreen<*>)?.let { RootScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: throw IllegalArgumentException(
      "A ScreenViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidScreen."
    )
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeScreenViewFactory]
 * than to call this method directly.
 *
 * Finds a [ScreenViewFactory] to create a [View] to display [initialRendering]. The new view
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
public fun <ScreenT : Screen> ViewEnvironment.buildView(
  initialRendering: ScreenT,
  contextForNewView: Context,
  container: ViewGroup? = null,
  initializeView: View.() -> Unit = { showFirstRendering() }
): View {
  val entry = getViewFactoryForRendering(initialRendering)
  val viewFactory = (entry as? ScreenViewFactory<ScreenT>)
    ?: error("Require a ScreenViewFactory for $initialRendering, found $entry")

  return viewFactory.buildView(
    initialRendering, this, contextForNewView, container
  ).also { view ->
    checkNotNull(view.showRenderingTag) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ScreenViewFactory::class.java.name} that created it."
    }
    initializeView.invoke(view)
  }
}

/**
 * Default implementation for the `initializeView` argument of [ViewEnvironment.buildView],
 * and for [DecorativeScreenViewFactory.initializeView]. Calls [showRendering] against
 * [getRendering] and [environment].
 */
@WorkflowUiExperimentalApi
public fun View.showFirstRendering() {
  showRendering(getRendering()!!, environment!!)
}
