package com.squareup.sample.timemachine.shakeable.internal

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * [FrameLayout] that can block all touch events.
 */
class GlassFrameLayout
  @JvmOverloads
  constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
  ) : FrameLayout(context, attrs, defStyleAttr) {

    var blockTouchEvents: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = blockTouchEvents
  }
