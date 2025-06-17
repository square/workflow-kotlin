package com.squareup.workflow1

import kotlinx.coroutines.yield

/**
 * Basic frame clock providing synchronization for the Workflow Runtime.
 */
public fun interface WorkflowFrameClock {

  /**
   * Resumes before the next 'frame' is processed.
   */
  public suspend fun resumeOnFrame(): Unit

  companion object {
    /**
     * The default 'frame clock' is simply to yield the dispatcher to let actions queue up.
     */
    val DEFAULT_FRAME_CLOCK = WorkflowFrameClock {
      yield()
    }
  }
}
