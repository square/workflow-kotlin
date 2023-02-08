package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.Wrapper

/**
 * An [Overlay] built around a root [content] [Screen].
 */
@WorkflowUiExperimentalApi
public interface ScreenOverlay<ContentT : Screen> : Overlay, Wrapper<Screen, ContentT> {
  public override val content: ContentT

  override fun <U : Screen> map(transform: (ContentT) -> U): ScreenOverlay<U>
}
