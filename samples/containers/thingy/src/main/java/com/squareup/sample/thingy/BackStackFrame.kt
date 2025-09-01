package com.squareup.sample.thingy

import com.squareup.workflow1.StatefulWorkflow.RenderContext
import com.squareup.workflow1.ui.Screen

internal interface BackStackFrame {
  val node: BackStackNode

  val isIdle: Boolean
    get() = false

  fun withIdle(): BackStackFrame = object : BackStackFrame by this {
    override val isIdle: Boolean
      get() = true
  }

  fun render(context: RenderContext<Any?, BackStackState, Any?>): Screen
}
