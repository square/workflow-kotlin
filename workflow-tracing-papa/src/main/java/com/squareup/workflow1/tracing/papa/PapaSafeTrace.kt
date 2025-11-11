package com.squareup.workflow1.tracing.papa

import androidx.tracing.Trace
import androidx.tracing.trace
import com.squareup.workflow1.tracing.SafeTraceInterface

/**
 * Production implementation of [SafeTraceInterface] that uses androidx.tracing.Trace.
 *
 * @param isTraceable Whether tracing is enabled. Clients should configure this directly.
 *   Defaults to false for backwards compatibility.
 */
class PapaSafeTrace(
  override val isTraceable: Boolean = false
) : SafeTraceInterface {

  override val isCurrentlyTracing: Boolean
    get() = Trace.isEnabled()

  override fun beginSection(label: String) {
    Trace.beginSection(label)
  }

  override fun endSection() {
    Trace.endSection()
  }

  override fun beginAsyncSection(
    name: String,
    cookie: Int
  ) {
    Trace.beginAsyncSection(name, cookie)
  }

  override fun endAsyncSection(
    name: String,
    cookie: Int
  ) {
    Trace.endAsyncSection(name, cookie)
  }

  override fun logSection(info: String) {
    trace(info) {}
  }
}
