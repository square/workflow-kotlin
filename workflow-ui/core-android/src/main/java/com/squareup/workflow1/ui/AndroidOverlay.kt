package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.container.Overlay
import com.squareup.workflow1.ui.container.OverlayDialogFactory

@WorkflowUiExperimentalApi
public interface AndroidOverlay<O: AndroidOverlay<O>>: Overlay {
  public val dialogFactory: OverlayDialogFactory<O>
}
