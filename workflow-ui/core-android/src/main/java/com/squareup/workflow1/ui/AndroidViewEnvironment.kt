package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeViewFactory]
 * than to call this method directly.
 *
 * Returns the [ViewFactory] that builds [View] instances suitable to display the given [rendering],
 * via subsequent calls to [View.showRendering].
 *
 * Prefers factories found via [ViewRegistry.getEntryFor]. If that returns null, falls
 * back to the factory provided by the rendering's implementation of
 * [AndroidViewRendering.viewFactory], if there is one. Note that this means that a
 * compile time [AndroidViewRendering.viewFactory] binding can be overridden at runtime.
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
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any>
  ViewEnvironment.getFactoryForRendering(rendering: RenderingT): ViewFactory<RenderingT> {
  @Suppress("UNCHECKED_CAST")
  return (get(ViewRegistry).getEntryFor(rendering::class) as? ViewFactory<RenderingT>)
    ?: (rendering as? AndroidViewRendering<*>)?.viewFactory as? ViewFactory<RenderingT>
    ?: (rendering as? Named<*>)?.let { NamedViewFactory as ViewFactory<RenderingT> }
    ?: throw IllegalArgumentException(
      "A ${ViewFactory::class.qualifiedName} should have been registered to display " +
        "${rendering::class.qualifiedName} instances, or that class should implement " +
        "${AndroidViewRendering::class.simpleName}<${rendering::class.simpleName}>."
    )
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeViewFactory]
 * than to call this method directly.
 *
 * Finds a [ViewFactory] to create a [View] to display [initialRendering]. The new view
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
 * created (that is, immediately after the call to [ViewFactory.buildView]).
 * [showRendering], [getRendering] and [environment] are all available when this is called.
 * Defaults to a call to [View.showFirstRendering].
 *
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 *
 * @throws IllegalStateException if the matching [ViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewEnvironment.buildView(
  initialRendering: RenderingT,
  contextForNewView: Context,
  container: ViewGroup? = null,
  initializeView: View.() -> Unit = { showFirstRendering() }
): View {
  val entry = getFactoryForRendering(initialRendering)
  val viewFactory = (entry as? ViewFactory<RenderingT>)
    ?: error("Require a ViewFactory for $initialRendering, found $entry")

  return viewFactory.buildView(
    initialRendering, this, contextForNewView, container
  ).also { view ->
    checkNotNull(view.showRenderingTag) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ViewFactory::class.java.name} that created it."
    }
    initializeView.invoke(view)
  }
}

/**
 * Default implementation for the `initializeView` argument of [ViewEnvironment.buildView],
 * and for [DecorativeViewFactory.initializeView]. Calls [showRendering] against
 * [getRendering] and [environment].
 */
@WorkflowUiExperimentalApi
public fun View.showFirstRendering() {
  showRendering(getRendering()!!, environment!!)
}
