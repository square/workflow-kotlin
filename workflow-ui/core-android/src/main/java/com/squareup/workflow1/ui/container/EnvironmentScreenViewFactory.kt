package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.DecorativeScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.merge

@WorkflowUiExperimentalApi
internal object EnvironmentScreenViewFactory : ScreenViewFactory<EnvironmentScreen<*>>
by DecorativeScreenViewFactory(
  type = EnvironmentScreen::class,
  unwrap = { withEnvironment, inheritedEnvironment ->
    Pair(
      withEnvironment.screen,
      inheritedEnvironment merge withEnvironment.viewEnvironment
    )
  }
)
