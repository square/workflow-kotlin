package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forBuiltView

/**
 * [ScreenViewFactory] that allows views to display instances of [NamedScreen]. Delegates
 * to the factory for [NamedScreen.wrapped].
 */
@WorkflowUiExperimentalApi
internal
fun <WrappedT : Screen> NamedScreenViewFactory() =
  forBuiltView<NamedScreen<WrappedT>> { namedScreen, environment, context, container ->
    namedScreen.wrapped.toViewFactory(environment)
      .unwrapping<NamedScreen<WrappedT>, WrappedT> { it.wrapped }
      .buildView(namedScreen, environment, context, container)
  }
