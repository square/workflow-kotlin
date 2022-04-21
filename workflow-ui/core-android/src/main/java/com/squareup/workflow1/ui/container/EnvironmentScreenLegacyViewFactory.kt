@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.DecorativeViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.merge

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
internal object EnvironmentScreenLegacyViewFactory : ViewFactory<EnvironmentScreen<*>>
by DecorativeViewFactory(
  type = EnvironmentScreen::class,
  map = { environmentScreen, inheritedEnvironment ->
    Pair(environmentScreen.wrapped, environmentScreen.environment merge inheritedEnvironment)
  }
)
