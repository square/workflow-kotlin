package com.squareup.sample.container

import com.squareup.sample.container.overviewdetail.OverviewDetailContainer
import com.squareup.sample.container.panel.PanelContainer
import com.squareup.sample.container.panel.ScrimContainer
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
val SampleContainers = ViewRegistry(
  OverviewDetailContainer, PanelContainer, ScrimContainer
)
