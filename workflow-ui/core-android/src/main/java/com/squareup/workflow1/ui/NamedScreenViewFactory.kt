package com.squareup.workflow1.ui

/**
 * [ViewFactory] that allows views to display instances of [Named]. Delegates
 * to the factory for [Named.wrapped].
 */
@WorkflowUiExperimentalApi
internal object NamedScreenViewFactory : ScreenViewFactory<NamedScreen<*>>
by DecorativeScreenViewFactory(NamedScreen::class, { named -> named.wrapped })
