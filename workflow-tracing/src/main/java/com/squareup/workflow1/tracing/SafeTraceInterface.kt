package com.squareup.workflow1.tracing

/**
 * Interface abstracting tracing functionality to allow for testing with fake implementations.
 */
interface SafeTraceInterface {
  val isTraceable: Boolean
  val isCurrentlyTracing: Boolean

  fun beginSection(label: String)
  fun endSection()
  fun beginAsyncSection(
    name: String,
    cookie: Int
  )

  fun endAsyncSection(
    name: String,
    cookie: Int
  )

  fun logSection(info: String)
}
