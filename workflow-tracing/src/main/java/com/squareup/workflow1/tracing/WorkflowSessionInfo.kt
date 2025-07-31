package com.squareup.workflow1.tracing

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession

/**
 * Little bundle of data that is cached by the [WorkflowRuntimeMonitor] for each [WorkflowSession].
 *
 * @param name - the shorter name of the workflow used for tracing.
 * @param key - the key that the workflow was rendered with (could be empty).
 */
public class WorkflowSessionInfo(
  public val name: String,
  public val key: String,
) {

  public constructor(
    session: WorkflowSession,
  ) : this(
    name = session.identifier.toWfLoggingName(),
    // Keys can be long, ellipsize
    key = session.renderKey.wfEllipsizeEnd(MAX_KEY_LENGTH)
  )

  public val logName: String = if (key.isEmpty()) {
    name
  } else {
    "$name($key)"
  }

  public val traceName: String = logName.wfEllipsizeEnd(MAX_TRACE_NAME_LENGTH)

  companion object {

    /**
     * Reasonable limits so that we can get more concatenated info into trace section labels capped
     * at 127 chars.
     */
    const val MAX_KEY_LENGTH: Int = 15
    const val MAX_TRACE_NAME_LENGTH: Int = 50
  }
}
