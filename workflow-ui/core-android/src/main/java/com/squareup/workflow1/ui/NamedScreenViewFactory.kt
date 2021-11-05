package com.squareup.workflow1.ui

/**
 * [ScreenViewFactory] that allows views to display instances of [NamedScreen]. Delegates
 * to the factory for [NamedScreen.wrapped].
 */
@WorkflowUiExperimentalApi
internal object NamedScreenViewFactory : ScreenViewFactory<NamedScreen<*>>
by DecorativeScreenViewFactory(NamedScreen::class, { named -> named.wrapped })
