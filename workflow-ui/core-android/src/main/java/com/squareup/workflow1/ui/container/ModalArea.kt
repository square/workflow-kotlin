package com.squareup.workflow1.ui.container

import android.graphics.Rect
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reports the the area of the screen whose events should be blocked by any modal [Overlay].
 * Expected to be supplied by containers that support [BodyAndModalsScreen].
 */
@WorkflowUiExperimentalApi
internal class ModalArea(
  val bounds: StateFlow<Rect>
) {
  companion object : ViewEnvironmentKey<ModalArea>(type = ModalArea::class) {
    override val default: ModalArea = ModalArea(MutableStateFlow(Rect()))
  }
}

@WorkflowUiExperimentalApi
internal operator fun ViewEnvironment.plus(modalArea: ModalArea): ViewEnvironment =
  this + (ModalArea to modalArea)
