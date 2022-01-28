package com.squareup.sample.container

import com.squareup.workflow1.ui.DecorativeViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

/**
 * [ViewFactory] that performs the work required by [BackButtonScreen].
 */
@WorkflowUiExperimentalApi
object BackButtonViewFactory : ViewFactory<BackButtonScreen<*>>
by DecorativeViewFactory(
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
