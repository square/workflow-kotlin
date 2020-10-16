package com.squareup.workflow1.ui

/**
 * [ViewBuilder] that allows views to display instances of [Named]. Delegates
 * to the builder for [Named.wrapped].
 */
@WorkflowUiExperimentalApi
object NamedViewBuilder : ViewBuilder<NamedViewRendering> by DecorativeViewBuilder(
    NamedViewRendering::class, { named -> named.wrapped }
)
