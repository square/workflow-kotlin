package com.squareup.sample.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.acceptRenderings
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.withShowScreen

/**
 * [ScreenViewFactory] that performs the work required by [BackButtonScreen], demonstrating
 * some fancy [ScreenViewHolder][com.squareup.workflow1.ui.ScreenViewHolder] tricks in
 * the process.
 */
@WorkflowUiExperimentalApi
val BackButtonViewFactory: ScreenViewFactory<BackButtonScreen<*>> =
  ScreenViewFactory.of { initialRenderingT, initialViewEnvironment, context, container ->
    initialRenderingT.wrapped
      // Build the view for the wrapped rendering.
      .buildView(initialViewEnvironment, context, container)
      // Transform it to accept BackButtonScreen directly
      .acceptRenderings<Screen, BackButtonScreen<*>> { backButtonScreen -> backButtonScreen.wrapped }
      // Replace the showScreen method with one that can do a bit of pre- and post-processing
      // on the view.
      .withShowScreen { backButtonScreen, viewEnvironment ->
        if (!backButtonScreen.override) {
          // Place our handler before invoking the real showRendering method, so that
          // its later calls to view.backPressedHandler will take precedence
          // over ours.
          view.backPressedHandler = backButtonScreen.onBackPressed
        }

        // The receiver of this lambda is the one that received the withShowScreen call,
        // so we're able to call the "real" showScreen method.
        showScreen(backButtonScreen, viewEnvironment)

        if (backButtonScreen.override) {
          // Place our handler after invoking innerShowRendering, so that ours wins.
          view.backPressedHandler = backButtonScreen.onBackPressed
        }
      }
  }
