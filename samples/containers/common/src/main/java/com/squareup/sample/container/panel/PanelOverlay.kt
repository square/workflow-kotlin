package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.AndroidOverlay

@OptIn(WorkflowUiExperimentalApi::class)
class PanelOverlay<T : Screen>(
  val content: T
) : AndroidOverlay<>
