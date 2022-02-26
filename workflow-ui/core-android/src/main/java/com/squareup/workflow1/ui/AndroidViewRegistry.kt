@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.BackStackScreen
import kotlin.reflect.KClass

@Deprecated("Use ScreenViewFactoryFinder.getViewFactoryForRenderingOrNull()")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.getFactoryForRendering(
  rendering: RenderingT
): ViewFactory<RenderingT> {
  @Suppress("UNCHECKED_CAST")
  return getFactoryFor(rendering::class)
    ?: (rendering as? AndroidViewRendering<*>)?.viewFactory as? ViewFactory<RenderingT>
    ?: (rendering as? AsScreen<*>)?.let { AsScreenLegacyViewFactory as ViewFactory<RenderingT> }
    ?: (rendering as? BackStackScreen<*>)?.let {
      BackStackScreenLegacyViewFactory as ViewFactory<RenderingT>
    }
    ?: (rendering as? Named<*>)?.let { NamedViewFactory as ViewFactory<RenderingT> }
    ?: throw IllegalArgumentException(
      "A ViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidViewRendering."
    )
}

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

@Deprecated("Use Screen.buildView")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null,
): View {
  return getFactoryForRendering(initialRendering).buildView(
    initialRendering, initialViewEnvironment, contextForNewView, container
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
