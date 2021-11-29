package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.graphics.Rect
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@WorkflowUiExperimentalApi
public fun Rect.toBounds(): Bounds {
  return Bounds(left, top, right, bottom)
}

@WorkflowUiExperimentalApi
public fun Dialog.setBounds(bounds: Bounds) {
  window?.let { window ->
    val windowRect = WindowManager.LayoutParams()
      .apply {
        copyFrom(window.attributes)
        gravity = Gravity.TOP or Gravity.START
      }
    windowRect.width = bounds.width
    windowRect.height = bounds.height
    windowRect.x = bounds.left
    windowRect.y = bounds.top

    window.attributes = windowRect
  }
}

@WorkflowUiExperimentalApi
public fun Dialog.maintainBounds(bounds: StateFlow<Bounds>) {
  val window = requireNotNull(window) { "Dialog must be attached to a window." }
  window.setFlags(FLAG_NOT_TOUCH_MODAL, FLAG_NOT_TOUCH_MODAL)

  window.callback = object : Window.Callback by window.callback {
    val scope = CoroutineScope(Dispatchers.Main.immediate)
    var job: Job? = null

    override fun onAttachedToWindow() {
      job = bounds.onEach { setBounds(it) }
        .launchIn(scope)
    }

    override fun onDetachedFromWindow() {
      job?.cancel()
      job = null
    }
  }

  // If already attached, set the bounds eagerly.
  if (window.peekDecorView()?.isAttachedToWindow == true) setBounds(bounds.value)
}
