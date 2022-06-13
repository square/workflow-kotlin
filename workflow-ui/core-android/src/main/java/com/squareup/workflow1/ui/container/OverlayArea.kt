package com.squareup.workflow1.ui.container

import android.graphics.Rect
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reports the the area of the screen whose events should be blocked by any modal [Overlay],
 * in the style reported by [View.getGlobalVisibleRect][android.view.View.getGlobalVisibleRect].
 * Expected to be supplied by containers that support [BodyAndOverlaysScreen].
 */
@WorkflowUiExperimentalApi
internal class OverlayArea(
  val bounds: StateFlow<Rect>
) {
  companion object : ViewEnvironmentKey<OverlayArea>(type = OverlayArea::class) {
    override val default: OverlayArea = OverlayArea(MutableStateFlow(Rect()))
  }
}

@WorkflowUiExperimentalApi
internal operator fun ViewEnvironment.plus(overlayArea: OverlayArea): ViewEnvironment =
  this + (OverlayArea to overlayArea)
