@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.BackStackScreen
import kotlin.reflect.KClass

@Deprecated("Use ViewEnvironment.getViewFactoryForRendering()")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any>
  ViewRegistry.getFactoryForRendering(rendering: RenderingT): ViewFactory<RenderingT> {
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

@Deprecated("Use getEntryFor()")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.getFactoryFor(
  renderingType: KClass<out RenderingT>
): ViewFactory<RenderingT>? {
  return getEntryFor(renderingType) as? ViewFactory<RenderingT>
}

@Suppress("DEPRECATION")
@Deprecated("Use ViewEnvironment.buildview")
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  initializeView: View.() -> Unit = { showFirstRendering() }
): View {
  return getFactoryForRendering(initialRendering).buildView(
    initialRendering, initialViewEnvironment, contextForNewView, container
  ).also { view ->
    checkNotNull(view.showRenderingTag) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ViewFactory::class.java.name} that created it."
    }
    initializeView.invoke(view)
  }
}
