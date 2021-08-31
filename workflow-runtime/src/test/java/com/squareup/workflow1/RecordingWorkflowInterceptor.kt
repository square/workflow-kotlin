@file:Suppress("SuspiciousCollectionReassignment")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession

/**
 * Workflow interceptor that records all received events in a list for testing.
 */
internal open class RecordingWorkflowInterceptor : SimpleLoggingWorkflowInterceptor() {

  private var events: List<String> = emptyList()

  override fun logBeforeMethod(
    name: String,
    session: WorkflowSession,
    vararg extras: Pair<String, Any?>
  ) {
    events += "BEGIN|$name"
  }

  override fun logAfterMethod(
    name: String,
    session: WorkflowSession,
    vararg extras: Pair<String, Any?>
  ) {
    events += "END|$name"
  }

  private fun consumeEvents(): List<String> = events
      .also { events = emptyList() }

  fun consumeEventNames(): List<String> = consumeEvents().map { it.substringBefore('(') }
}
