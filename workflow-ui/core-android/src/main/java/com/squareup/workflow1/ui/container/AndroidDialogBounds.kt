package com.squareup.workflow1.ui.container

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Updates the size of the [Window] of the receiving [Dialog].
 * [bounds] is expected to be in global screen coordinates,
 * as returned from [View.getScreenRect].
 *
 * @see OverlayDialogHolder.onUpdateBounds
 */
@WorkflowUiExperimentalApi
public fun Dialog.setBounds(bounds: Rect) {
  window?.let {
    it.addFlags(FLAG_LAYOUT_IN_SCREEN)
    it.attributes = it.attributes.apply {
      // Our absolute coordinates are independent from Rtl.
      @SuppressLint("RtlHardcoded")
      gravity = Gravity.LEFT or Gravity.TOP // we use absolute coordinates.
      width = bounds.width()
      height = bounds.height()
      x = bounds.left
      y = bounds.top
    }
  }
}

/**
 * Returns the bounds of this [View] in the coordinate space of the device
 * screen, based on [View.getLocationOnScreen] and its reported [width][View.getWidth]
 * and [height][View.getHeight].
 */
@WorkflowUiExperimentalApi
public fun View.getScreenRect(rect: Rect) {
  val coordinates = IntArray(2)
  getLocationOnScreen(coordinates)
  rect.apply {
    left = coordinates[0]
    top = coordinates[1]
    right = left + width
    bottom = top + height
  }
}

@WorkflowUiExperimentalApi
internal fun <D : Dialog> D.maintainBounds(
  environment: ViewEnvironment,
  onBoundsChange: (Rect) -> Unit
) {
  maintainBounds(environment[OverlayArea].bounds, onBoundsChange)
}

@WorkflowUiExperimentalApi
private fun <D : Dialog> D.maintainBounds(
  bounds: StateFlow<Rect>,
  onBoundsChange: (Rect) -> Unit
) {
  val window = requireNotNull(window) { "Dialog must be attached to a window." }
  window.callback = object : Window.Callback by window.callback {
    var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
      scope = CoroutineScope(Dispatchers.Main.immediate).also {
        bounds.onEach { b -> onBoundsChange(b) }
          .launchIn(it)
      }
    }

    override fun onDetachedFromWindow() {
      scope?.cancel()
      scope = null
    }
  }

  // If already attached, set the bounds eagerly.
  if (window.peekDecorView()?.isAttachedToWindow == true) onBoundsChange(bounds.value)
}
