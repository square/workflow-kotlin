package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Interface implemented by a rendering class to allow it to drive an Android UI
 * via an appropriate `@Composable` [Content] function.
 */
@WorkflowUiExperimentalApi
public interface ComposeScreen<S : ComposeScreen<S>> : Screen {

  /**
   * The composable content of this rendering. This method will be called
   * any time a new rendering is emitted, or the [viewEnvironment]
   * changes.
   *
   * This function will either serve as the root of a
   * [ComposeView][androidx.compose.ui.platform.ComposeView], or else
   * be called as the child of a [Box][androidx.compose.foundation.layout.Box]
   */
  @Suppress("FunctionName")
  @Composable public fun Content(viewEnvironment: ViewEnvironment)
}
