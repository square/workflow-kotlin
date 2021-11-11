package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
@Suppress("DEPRECATION")
internal object AsScreenViewFactory : ScreenViewFactory<AsScreen<*>>
by ManualScreenViewFactory(
  type = AsScreen::class,
  viewConstructor = { initialRendering, initialViewEnvironment, context, container ->
    initialViewEnvironment[ViewRegistry]
      .buildView(
        initialRendering.rendering,
        initialViewEnvironment,
        context,
        container,
        // Don't call showRendering yet, we need to wrap the function first.
        initializeView = { }
      ).also { view ->
        val legacyShowRendering = view.getShowRendering<Any>()!!

        view.bindShowRendering(
          initialRendering,
          initialViewEnvironment
        ) { rendering, env -> legacyShowRendering(rendering.rendering, env) }

        view.showFirstRendering()
      }
  }
)
