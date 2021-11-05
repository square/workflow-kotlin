package com.squareup.workflow1.ui

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
internal object AsScreenLegacyViewFactory : ViewFactory<AsScreen<*>>
by DecorativeViewFactory(AsScreen::class, { asScreen -> asScreen.rendering })
