package com.squareup.workflow1.ui

/**
 * [ViewFactory] that allows views to display instances of [Named]. Delegates
 * to the factory for [Named.wrapped].
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
internal object NamedViewFactory : ViewFactory<Named<*>>
by DecorativeViewFactory(Named::class, { named -> named.wrapped })
