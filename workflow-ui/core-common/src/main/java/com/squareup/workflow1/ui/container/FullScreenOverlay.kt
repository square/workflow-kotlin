package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A basic [ScreenOverlay] that covers its container with the wrapped [content] [Screen].
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class FullScreenOverlay<C : Screen>(
  public override val content: C
) : ScreenOverlay<C> {
  override fun <D : Screen> map(transform: (C) -> D): FullScreenOverlay<D> =
    FullScreenOverlay(transform(content))
}
