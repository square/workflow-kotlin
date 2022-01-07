package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.acceptRenderings
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.updateFrom
import com.squareup.workflow1.ui.withShowScreen

@WorkflowUiExperimentalApi
internal val EnvironmentScreenViewFactory: ScreenViewFactory<EnvironmentScreen<*>> =
  ScreenViewFactory.of { initialRendering, initialViewEnvironment, context, container ->
    initialRendering.screen
      // Build the view for the wrapped rendering.
      .buildView(initialViewEnvironment, context, container)
      // Transform it to accept EnvironmentScreen directly.
      .acceptRenderings<Screen, EnvironmentScreen<*>> { it.screen }
      // When showScreen is called, enhance the viewEnvironment with the one in environmentScreen.
      .withShowScreen { environmentScreen, viewEnvironment ->
        showScreen(environmentScreen, viewEnvironment.updateFrom(environmentScreen.viewEnvironment))
      }
  }
