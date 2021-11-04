package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.DecorativeScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.updateFrom

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
