package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.DecorativeScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.updateFrom

@WorkflowUiExperimentalApi
internal object WithEnvironmentViewFactory : ScreenViewFactory<WithEnvironment<*>>
by DecorativeScreenViewFactory(
  type = WithEnvironment::class,
  map = { withEnvironment, inheritedEnvironment ->
    Pair(
      withEnvironment.screen,
      inheritedEnvironment.updateFrom(withEnvironment.viewEnvironment)
    )
  }
)
