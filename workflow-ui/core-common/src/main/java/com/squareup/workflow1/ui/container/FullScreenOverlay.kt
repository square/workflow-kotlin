package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A basic [ScreenOverlay] that covers its container with the wrapped [content] [Screen].
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class FullScreenOverlay<ContentT : Screen>(
  public override val content: ContentT
) : ScreenOverlay<ContentT>
