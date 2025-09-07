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
 * Convenience function to wrap [block] with a trace span as defined by [WorkflowTracer]. This
 * wraps very frequently evaluated code and we should only use constants for [label], with no
 * interpolation.
 */
public inline fun <T> WorkflowTracer?.trace(
  label: String,
  block: () -> T
): T {
  return if (this == null) {
    block()
  } else {
    beginSection(label)
    try {
      return block()
    } finally {
      endSection()
    }
  }
}

public inline fun <T> WorkflowTracer?.traceNoFinally(
  label: String,
  block: () -> T
): T {
  this?.beginSection(label)
  val result = block()
  this?.endSection()
  return result
}
