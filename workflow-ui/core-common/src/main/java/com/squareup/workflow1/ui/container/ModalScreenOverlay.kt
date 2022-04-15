package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A basic [ScreenOverlay] that fills all available space.
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class ModalScreenOverlay<ContentT : Screen>(
  public override val content: ContentT
) : ScreenOverlay<ContentT>
