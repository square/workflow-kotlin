package com.squareup.workflow1.ui

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
internal fun AsScreenViewFactory(
  initialRendering: AsScreen<*>,
  initialViewEnvironment: ViewEnvironment
): ScreenViewFactory<AsScreen<*>> {
  val wrapped = initialRendering.rendering
  val registry = initialViewEnvironment[ViewRegistry]

  return ScreenViewFactory<AsScreen<*>>(
    buildView = { environment, context, container ->
      registry.buildView(wrapped, environment, context, container).also { view ->
        view.getTag(R.id.workflow_legacy_show_rendering)?.let {
          error("AsScreen does not support recursion, found existing ViewShowRendering: $it")
        }

        // Capture the legacy showRendering function so that we can call it from our own
        // updateView.
        val legacyShowRendering = view.getShowRendering<Any>()!!
        view.setTag(R.id.workflow_legacy_show_rendering, legacyShowRendering)

        // Like any decorator, we need to call bindShowRendering again to
        // ensure that the wrapper initialRendering is in place for View.getRendering() calls.
        // Note that we're careful to preserve the ViewEnvironment put in place by the
        // legacy ViewFactory
        view.bindShowRendering(initialRendering, view.environment!!) { _, _ ->
          // We leave a no-op (this lambda) in place for View.showRendering(),
          // but ScreenViewFactory.start() will soon put something else in its place.
        }
      }
    },
    updateView = { view, asScreen, environment ->
      @Suppress("UNCHECKED_CAST")
      val legacyShowRendering =
        view.getTag(R.id.workflow_legacy_show_rendering) as ViewShowRendering<Any>
      legacyShowRendering(asScreen.rendering, environment)
    }
  )
}
