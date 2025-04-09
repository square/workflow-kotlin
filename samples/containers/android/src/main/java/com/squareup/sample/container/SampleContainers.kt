package com.squareup.sample.container

import com.squareup.sample.container.overviewdetail.OverviewDetailContainer
import com.squareup.sample.container.panel.PanelOverlayDialogFactory
import com.squareup.workflow1.ui.ViewRegistry

val SampleContainers = ViewRegistry(
  OverviewDetailContainer,
  PanelOverlayDialogFactory,
  ScrimContainer
)
