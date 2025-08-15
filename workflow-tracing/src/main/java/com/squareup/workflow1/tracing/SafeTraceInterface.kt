package com.squareup.workflow1.tracing

/**
 * Interface abstracting tracing functionality to allow for testing with fake implementations.
 */
public interface SafeTraceInterface {
  public val isTraceable: Boolean
  public val isCurrentlyTracing: Boolean

  public fun beginSection(label: String)
  public fun endSection()
  public fun beginAsyncSection(
    name: String,
    cookie: Int
  )

  public fun endAsyncSection(
    name: String,
    cookie: Int
  )

  public fun logSection(info: String)
}
