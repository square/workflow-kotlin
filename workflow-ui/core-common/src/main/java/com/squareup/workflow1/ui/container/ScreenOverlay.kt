package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * An [Overlay] built around a root [content] [Screen].
 */
@WorkflowUiExperimentalApi
public interface ScreenOverlay<ContentT : Screen> : Overlay {
  public val content: ContentT
}
