package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
@Suppress("DEPRECATION")
internal object AsScreenViewFactory : ScreenViewFactory<AsScreen<*>>
by ScreenViewFactory.of(
  viewConstructor = { initialRendering, initialViewEnvironment, context, container ->
    initialViewEnvironment[ViewRegistry]
      .buildView(
        initialRendering.rendering,
        initialViewEnvironment,
        context,
        container
      ).let { view ->
        val legacyShowRendering = view.getShowRendering<Any>()!!

        ScreenViewHolder(
          initialRendering,
          initialViewEnvironment,
          view
        ) { rendering, env -> legacyShowRendering(rendering.rendering, env) }
      }
  }
)
