package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * An [Overlay] built around a root [content] [Screen].
 */
@WorkflowUiExperimentalApi
public interface ScreenOverlay<ContentT : Screen> : Overlay, Compatible {
  public val content: ContentT

  override val compatibilityKey: String
    get() = keyFor(content, this::class.simpleName ?: ScreenOverlay::class.simpleName!!)
}
