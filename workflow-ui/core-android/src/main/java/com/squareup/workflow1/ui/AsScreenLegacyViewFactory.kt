package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
internal object AsScreenLegacyViewFactory : ViewFactory<AsScreen<*>>
by DecorativeViewFactory(AsScreen::class, { asScreen -> asScreen.rendering})
