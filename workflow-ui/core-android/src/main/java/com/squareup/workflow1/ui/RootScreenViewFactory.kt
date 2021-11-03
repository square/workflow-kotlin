package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.container.RootScreen

@WorkflowUiExperimentalApi
internal object RootScreenViewFactory : ScreenViewFactory<RootScreen<*>>
by DecorativeScreenViewFactory(
  type = RootScreen::class,
  map = { rootScreen, inheritedEnvironment ->
    Pair(
      rootScreen.screen,
      inheritedEnvironment.updateFrom(rootScreen.viewEnvironment)
    )
  }
)
