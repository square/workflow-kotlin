package com.squareup.workflow1.ui

/**
 * [ScreenViewFactory] that allows views to display instances of [NamedScreen]. Delegates
 * to the factory for [NamedScreen.wrapped].
 */
@WorkflowUiExperimentalApi
internal fun NamedScreenViewFactory(
  initialRendering: NamedScreen<*>,
  initialViewEnvironment: ViewEnvironment
): ScreenViewFactory<NamedScreen<*>> {
  val wrappedFactory = initialRendering.wrapped.toViewFactory(initialViewEnvironment)

  return ScreenViewFactory(
    buildView = wrappedFactory::buildView,
    updateView = { view, rendering, environment ->
      wrappedFactory.updateView(view, rendering.wrapped, environment)
    }
  )
}
