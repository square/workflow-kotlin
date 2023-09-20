@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenLegacyViewFactory
import kotlin.reflect.KClass

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeViewFactory]
 * than to call this method directly.
 *
 * Returns the [ViewFactory] that builds [View] instances suitable to display the given [rendering],
 * via subsequent calls to [View.showRendering].
 *
 * Prefers factories found via [ViewRegistry.getFactoryFor]. If that returns null, falls
 * back to the factory provided by the rendering's implementation of
 * [AndroidViewRendering.viewFactory], if there is one. Note that this means that a
 * compile time [AndroidViewRendering.viewFactory] binding can be overridden at runtime.
 *
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 */
@Deprecated("Use ScreenViewFactoryFinder.getViewFactoryForRendering()")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.getFactoryForRendering(
  rendering: RenderingT
): ViewFactory<RenderingT> {
  @Suppress("UNCHECKED_CAST")
  return getFactoryFor(rendering::class)
    ?: (rendering as? AndroidViewRendering<*>)?.viewFactory as? ViewFactory<RenderingT>
    ?: (rendering as? Named<*>)?.let { NamedViewFactory as ViewFactory<RenderingT> }
    ?: (rendering as? AsScreen<*>)?.let { AsScreenLegacyViewFactory as ViewFactory<RenderingT> }
    ?: (rendering as? EnvironmentScreen<*>)?.let {
      // Special handling to ensure the custom environment is in play before the view is built.
      EnvironmentScreenLegacyViewFactory as ViewFactory<RenderingT>
    }
    ?: (rendering as? Screen)?.let { LegacyFactoryForScreenType() as ViewFactory<RenderingT> }
    ?: throw IllegalArgumentException(
      "A ViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidViewRendering."
    )
}

/**
 * This method is not for general use, use [WorkflowViewStub] instead.
 *
 * Returns the [ViewFactory] that was registered for the given [renderingType], or null
 * if none was found.
 */
@Deprecated(
  "Use getEntryFor()",
  ReplaceWith("getEntryFor(renderingType)")
)
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.getFactoryFor(
  renderingType: KClass<out RenderingT>
): ViewFactory<RenderingT>? {
  return getEntryFor(renderingType) as? ViewFactory<RenderingT>
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeViewFactory]
 * than to call this method directly.
 *
 * Finds a [ViewFactory] to create a [View] ready to display [initialRendering]. The caller
 * is responsible for calling [View.start] on the new [View]. After that,
 * [View.showRendering] can be used to update it with new renderings that
 * are [compatible] with [initialRendering].
 *
 * @param viewStarter An optional wrapper for the function invoked when [View.start]
 * is called, allowing for last second initialization of a newly built [View].
 * See [ViewStarter] for details.
 *
 * @throws IllegalArgumentException if no factory can be found for type [RenderingT]
 *
 * @throws IllegalStateException if the matching [ViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@Deprecated("Use Screen.toViewFactory and ScreenViewFactory.startShowing")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null,
): View {
  return getFactoryForRendering(initialRendering).buildView(
    initialRendering,
    initialViewEnvironment,
    contextForNewView,
    container
  ).also { view ->
    checkNotNull(view.workflowViewStateOrNull) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "ViewFactory that created it."
    }
    viewStarter?.let { givenStarter ->
      val doStart = view.starter
      view.starter = { newView ->
        givenStarter.startView(newView) { doStart.invoke(newView) }
      }
    }
  }
}
