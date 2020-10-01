package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

@WorkflowUiExperimentalApi
interface ViewRendering

@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> RenderingT.buildView(
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null
): View {
  val viewFactory = initialViewEnvironment[ViewRegistry].getEntryFor(this::class)
  require(viewFactory is ViewFactory<RenderingT>) {
    "A ${ViewFactory::class.java.name} should have been registered " +
        "to display a ${this::class}, instead found $viewFactory."
  }

  return viewFactory
      .buildView(
          this,
          initialViewEnvironment,
          contextForNewView,
          container
      )
      .apply {
        check(this.getRendering<Any>() != null) {
          "View.bindShowRendering should have been called for $this, typically by the " +
              "${ViewFactory::class.java.name} that created it."
        }
      }
}

@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> RenderingT.buildView(
  initialViewEnvironment: ViewEnvironment,
  container: ViewGroup
): View = buildView(initialViewEnvironment, container.context, container)
