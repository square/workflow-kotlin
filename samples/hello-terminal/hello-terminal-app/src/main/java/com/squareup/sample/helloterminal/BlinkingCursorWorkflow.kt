package com.squareup.sample.helloterminal

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import kotlinx.coroutines.delay

/**
 * A simple workflow that renders either a cursor character or an empty string, changing after every
 * [delayMs] milliseconds.
 */
class BlinkingCursorWorkflow(
  cursor: Char,
  private val delayMs: Long
) : StatefulWorkflow<Unit, Boolean, Nothing, String>() {

  private val cursorString = cursor.toString()

  private val intervalWorker = Worker.create {
    var on = true
    while (true) {
      emit(on)
      delay(delayMs)
      on = !on
    }
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): Boolean = true

  override fun render(
    props: Unit,
    state: Boolean,
    context: RenderContext
  ): String {
    context.runningWorker(intervalWorker) { setCursorShowing(it) }
    return if (state) cursorString else ""
  }

  override fun snapshotState(state: Boolean): Snapshot? = null

  private fun setCursorShowing(showing: Boolean) = action {
    state = showing
  }
}
