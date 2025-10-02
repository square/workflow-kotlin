package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A basic [ScreenOverlay] that covers its container with the wrapped [content] [Screen].
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class FullScreenModal<out C : Screen>(
  public override val content: C
) : ScreenOverlay<C>, ModalOverlay {
  override fun <D : Screen> map(transform: (C) -> D): FullScreenModal<D> =
    FullScreenModal(transform(content))
}
