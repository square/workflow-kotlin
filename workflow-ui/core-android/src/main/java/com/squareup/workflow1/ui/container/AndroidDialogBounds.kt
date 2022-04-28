package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.Window
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Updates the size of the [Window] of the receiving [Dialog].
 * [bounds] is expected to be in global display coordinates,
 * e.g. as returned from [View.getGlobalVisibleRect].
 *
 * @see ModalScreenOverlayDialogFactory.updateBounds
 */
@WorkflowUiExperimentalApi
public fun Dialog.setBounds(bounds: Rect) {
  window?.let {
    it.attributes = it.attributes.apply {
      gravity = Gravity.TOP or Gravity.START
      width = bounds.width()
      height = bounds.height()
      x = bounds.left
      y = bounds.top
    }
  }
}

@WorkflowUiExperimentalApi
internal fun <D : Dialog> D.maintainBounds(
  environment: ViewEnvironment,
  onBoundsChange: (D, Rect) -> Unit
) {
  maintainBounds(environment[ModalArea].bounds, onBoundsChange)
}

@WorkflowUiExperimentalApi
internal fun <D : Dialog> D.maintainBounds(
  bounds: StateFlow<Rect>,
  onBoundsChange: (D, Rect) -> Unit
) {
  val window = requireNotNull(window) { "Dialog must be attached to a window." }
  window.callback = object : Window.Callback by window.callback {
    var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
      scope = CoroutineScope(Dispatchers.Main.immediate).also {
        bounds.onEach { b -> onBoundsChange(this@maintainBounds, b) }
          .launchIn(it)
      }
    }

    override fun onDetachedFromWindow() {
      scope?.cancel()
      scope = null
    }
  }

  // If already attached, set the bounds eagerly.
  if (window.peekDecorView()?.isAttachedToWindow == true) onBoundsChange(this, bounds.value)
}
