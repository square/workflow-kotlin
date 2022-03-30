package com.squareup.workflow1.ui

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
internal fun AsScreenViewFactory(
  initialRendering: AsScreen<*>,
  initialViewEnvironment: ViewEnvironment
): ScreenViewFactory<AsScreen<*>> {
  val wrapped = initialRendering.rendering
  val registry = initialViewEnvironment[ViewRegistry]

  return ScreenViewFactory.forBuiltView { _, environment, context, container ->
    registry.buildView(wrapped, environment, context, container).let { view ->
      // Capture the legacy showRendering function so that we can call it from our own
      // ScreenViewHolder.
      val legacyShowRendering = view.getShowRendering<Any>()!!

      // Like any legacy decorator, we need to call bindShowRendering again to
      // ensure that the wrapper initialRendering is in place for View.getRendering() calls.
      // Note that we're careful to preserve the ViewEnvironment put in place by the
      // legacy ViewFactory
      view.bindShowRendering(initialRendering, view.environment!!) { _, _ ->
        // We leave a no-op (this lambda) in place for View.showRendering(),
        // but ScreenViewFactory.start() will soon put something else in its place.
      }

      ScreenViewHolder(environment, view) { asScreen, newEnv ->
        legacyShowRendering(asScreen.rendering, newEnv)
      }
    }
  }
}
