package com.squareup.workflow1.ui

/**
 * [ScreenViewFactory] that allows views to display instances of [NamedScreen]. Delegates
 * to the factory for [NamedScreen.wrapped].
 */
@WorkflowUiExperimentalApi
internal val NamedScreenViewFactory: ScreenViewFactory<NamedScreen<*>> =
  ScreenViewFactory.of { initialRendering, initialViewEnvironment, context, container ->
    initialRendering.wrapped.buildView(initialViewEnvironment, context, container)
      .acceptRenderings { named -> named.wrapped }
  }
