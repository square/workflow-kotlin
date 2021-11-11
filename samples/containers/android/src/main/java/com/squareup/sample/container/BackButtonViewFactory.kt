package com.squareup.sample.container

import com.squareup.workflow1.ui.DecorativeScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

/**
 * [ScreenViewFactory] that performs the work required by [BackButtonScreen].
 */
@WorkflowUiExperimentalApi
object BackButtonViewFactory : ScreenViewFactory<BackButtonScreen<*>>
by DecorativeScreenViewFactory(
  type = BackButtonScreen::class,
  map = { outer -> outer.wrapped },
  doShowRendering = { view, innerShowRendering, outerRendering, viewEnvironment ->
    if (!outerRendering.override) {
      // Place our handler before invoking innerShowRendering, so that
      // its later calls to view.backPressedHandler will take precedence
      // over ours.
      view.backPressedHandler = outerRendering.onBackPressed
    }

    innerShowRendering.invoke(outerRendering.wrapped, viewEnvironment)

    if (outerRendering.override) {
      // Place our handler after invoking innerShowRendering, so that ours wins.
      view.backPressedHandler = outerRendering.onBackPressed
    }
  }
)
