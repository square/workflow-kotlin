package com.squareup.workflow1

/**
 * This is a very simple tracing interface that can be passed into a workflow runtime in order
 * to inject span tracing throughout the workflow core and runtime internals.
 */
public interface WorkflowTracer {
  public fun beginSection(label: String): Unit
  public fun endSection(): Unit
}

/**
 * Convenience function to wrap [block] with a trace span as defined by [WorkflowTracer].
 * Only calls [label] if there is an active [WorkflowTracer] use this for any label other than
 * a constant.
 */
public inline fun <T> WorkflowTracer?.trace(label: () -> String, block: () -> T): T {
  val optimizedLabel = if (this !== null) {
    label()
  } else {
    ""
  }
  return trace(optimizedLabel, block)
}

/**
 * Convenience function to wrap [block] with a trace span as defined by [WorkflowTracer].
 */
public inline fun <T> WorkflowTracer?.trace(label: String, block: () -> T): T {
  this?.beginSection(label)
  try {
    return block()
  } finally {
    this?.endSection()
  }
}
