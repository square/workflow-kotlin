package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reports the the area of the screen whose events should be blocked by any modal [Overlay].
 */
@WorkflowUiExperimentalApi
public class ModalArea(
  public val bounds: StateFlow<Bounds>
) {
  public companion object : ViewEnvironmentKey<ModalArea>(type = ModalArea::class) {
    override val default: ModalArea = ModalArea(MutableStateFlow(Bounds()))
  }
}
