package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forBuiltView

/**
 * [ScreenViewFactory] that allows views to display instances of [NamedScreen]. Delegates
 * to the factory for [NamedScreen.wrapped].
 */
@WorkflowUiExperimentalApi
internal
fun <WrappedT : Screen> NamedScreenViewFactory(): ScreenViewFactory<NamedScreen<WrappedT>> {
  return forBuiltView { initialNamedScreen, initialEnvironment, context, container ->
    initialNamedScreen.wrapped.toViewFactory(initialEnvironment)
      .unwrapping<NamedScreen<WrappedT>, WrappedT> { it.wrapped }
      .buildView(initialNamedScreen, initialEnvironment, context, container)
  }
}
