package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering

/**
 * Bridge between [ScreenViewFactory] and [ScreenComposableFactory], and thus between
 * classic [View]s and Compose UI.
 */
@WorkflowUiExperimentalApi
public abstract class ComposeScreenViewFactory<RenderingT : Screen> : ScreenViewFactory<RenderingT>,
  ScreenComposableFactory<RenderingT> {

  final override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View = ComposeView(contextForNewView).also { composeView ->
    // Update the state whenever a new rendering is emitted.
    // This lambda will be executed synchronously before bindShowRendering returns.
    composeView.bindShowRendering(
      initialRendering,
      initialViewEnvironment
    ) { rendering, environment ->
      // Entry point to the world of Compose.
      composeView.setContent {
        Content(rendering, environment)
      }
    }
  }
}
