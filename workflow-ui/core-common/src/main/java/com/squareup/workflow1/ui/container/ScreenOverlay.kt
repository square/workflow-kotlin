package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * An [Overlay] built around a root [content] [Screen].
 */
@WorkflowUiExperimentalApi
public interface ScreenOverlay<ContentS : Screen> : Overlay, Compatible {
  public val content: ContentS

  override val compatibilityKey: String get() = keyFor(content, "ScreenOverlay")
}
