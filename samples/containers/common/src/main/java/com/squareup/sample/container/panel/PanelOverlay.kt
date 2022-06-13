package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.ModalOverlay
import com.squareup.workflow1.ui.container.ScreenOverlay

@OptIn(WorkflowUiExperimentalApi::class)
class PanelOverlay<T : Screen>(
  override val content: T
) : ScreenOverlay<T>, ModalOverlay
