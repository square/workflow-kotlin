@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.DecorativeViewFactory
import com.squareup.workflow1.ui.EnvironmentScreen
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
internal object EnvironmentScreenLegacyViewFactory : ViewFactory<EnvironmentScreen<*>>
by DecorativeViewFactory(
  type = EnvironmentScreen::class,
  map = { environmentScreen, inheritedEnvironment ->
    Pair(environmentScreen.content, environmentScreen.environment + inheritedEnvironment)
  }
)
