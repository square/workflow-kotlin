package com.squareup.workflow1.ui.container

import android.os.SystemClock
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL

internal fun dispatchCancelEvent(dispatchTouchEvent: (MotionEvent) -> Unit) {
  val now = SystemClock.uptimeMillis()
  MotionEvent.obtain(now, now, ACTION_CANCEL, 0.0f, 0.0f, 0).let { cancelEvent ->
    dispatchTouchEvent(cancelEvent)
    cancelEvent.recycle()
  }
}
