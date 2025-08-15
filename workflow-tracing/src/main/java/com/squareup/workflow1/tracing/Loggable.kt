package com.squareup.workflow1.tracing

/**
 * Optional interface implemented by workflow `PropsT`, `StateT` `OutputT` classes
 * to customize their logging names for [WorkflowRuntimeMonitor] output.
 */
public interface Loggable {
  public fun toLogString(): String
}
