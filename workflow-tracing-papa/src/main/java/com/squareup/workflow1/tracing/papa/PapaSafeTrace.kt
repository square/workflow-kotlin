package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.SafeTraceInterface
import papa.SafeTrace

/**
 * Production implementation of [SafeTraceInterface] that delegates to the actual [SafeTrace].
 */
class PapaSafeTrace : SafeTraceInterface {
  override val isTraceable: Boolean
    get() = SafeTrace.isTraceable

  override val isCurrentlyTracing: Boolean
    get() = SafeTrace.isCurrentlyTracing

  override fun beginSection(label: String) {
    SafeTrace.beginSection(label)
  }

  override fun endSection() {
    SafeTrace.endSection()
  }

  override fun beginAsyncSection(
    name: String,
    cookie: Int
  ) {
    SafeTrace.beginAsyncSection(name, cookie)
  }

  override fun endAsyncSection(
    name: String,
    cookie: Int
  ) {
    SafeTrace.endAsyncSection(name, cookie)
  }

  override fun logSection(info: String) {
    SafeTrace.logSection(info)
  }
}
