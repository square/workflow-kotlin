package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.ModalOverlay
import com.squareup.workflow1.ui.container.ScreenOverlay

@OptIn(WorkflowUiExperimentalApi::class)
class PanelOverlay<C : Screen>(
  override val content: C
) : ScreenOverlay<C>, ModalOverlay {
  override fun <D : Screen> map(transform: (C) -> D): PanelOverlay<D> =
    PanelOverlay(transform(content))
}
