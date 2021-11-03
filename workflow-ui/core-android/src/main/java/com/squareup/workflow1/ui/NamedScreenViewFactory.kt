package com.squareup.workflow1.ui

/**
 * [ViewFactory] that allows views to display instances of [Named]. Delegates
 * to the factory for [Named.wrapped].
 */
@WorkflowUiExperimentalApi
internal object NamedScreenViewFactory : ScreenViewFactory<NamedView<*>>
by DecorativeScreenViewFactory(NamedView::class, { named -> named.wrapped })
