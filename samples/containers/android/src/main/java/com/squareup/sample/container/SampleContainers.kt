package com.squareup.sample.container

import com.squareup.sample.container.overviewdetail.OverviewDetailContainer
import com.squareup.sample.container.panel.PanelOverlayDialogFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
val SampleContainers = ViewRegistry(
  BackButtonViewFactory, OverviewDetailContainer, PanelOverlayDialogFactory, ScrimContainer
)
