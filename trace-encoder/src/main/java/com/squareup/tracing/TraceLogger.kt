package com.squareup.tracing

/**
 * [Logs][log] [TraceEvent]s to a [TraceEncoder] under a given process and thread name.
 *
 * Create with [TraceEncoder.createLogger].
 */
public interface TraceLogger {

  /**
   * Tags all events with the current timestamp and then enqueues them to be written to the trace
   * file.
   */
  public fun log(eventBatch: List<TraceEvent>)

  /**
   * Tags event with the current timestamp and then enqueues it to be written to the trace
   * file.
   */
  public fun log(event: TraceEvent)
}
