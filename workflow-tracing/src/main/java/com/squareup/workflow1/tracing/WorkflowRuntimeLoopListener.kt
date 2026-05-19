package com.squareup.workflow1.tracing

/**
 * Can be passed to a [WorkflowRuntimeMonitor] listen for every runtime loop that executes. The list
 * of [RuntimeUpdates] in this runtime loop will be provided.
 *
 * This can be extremely useful for establishing a "trail of breadcrumbs" for what your
 * application has done.
 */
public fun interface WorkflowRuntimeLoopListener {

  /**
   * Called whenever the runtime loop completes with all the update events that have happened in
   * that loop.
   */
  public fun onRuntimeLoopSettled(
    configSnapshot: ConfigSnapshot,
    runtimeUpdates: RuntimeUpdates
  )
}

/**
 * Simple wrapper object for a list of [RuntimeUpdateLogLine]s. This allows us to add updates
 * and provides a [readAndClear] API to clear the list after it is read.
 */
public class RuntimeUpdates internal constructor(
  private val maxLogLineLength: Int = WorkflowRuntimeMonitor.DEFAULT_MAX_LOG_LINE_LENGTH,
  private val crashOnLogLineOverflow: Boolean = false,
) {
  init {
    require(maxLogLineLength > 0) {
      "maxLogLineLength must be greater than 0."
    }
  }

  private val updateLines = mutableListOf<RuntimeUpdateLogLine>()
  internal fun logUpdate(updateLine: RuntimeUpdateLogLine) {
    updateLines += updateLine.withLogLimits(
      maxLogLineLength = maxLogLineLength,
      crashOnLogLineOverflow = crashOnLogLineOverflow
    )
  }

  /**
   * Get the list of [RuntimeUpdateLogLine]s and then clear it.
   */
  public fun readAndClear(): List<RuntimeUpdateLogLine> = updateLines.toList().also {
    updateLines.clear()
  }
}
