@file:Suppress("DEPRECATION")

package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.FakeTrace
import com.squareup.workflow1.tracing.SafeTraceInterface

@Deprecated(
  message = "Renamed to FakeTrace and moved to com.squareup.workflow1.tracing package",
  replaceWith = ReplaceWith(
    expression = "FakeTrace",
    imports = arrayOf("com.squareup.workflow1.tracing.FakeTrace")
  )
)
class FakeSafeTrace(
  isTraceable: Boolean = true,
  isCurrentlyTracing: Boolean = true
) : SafeTraceInterface {
  private val delegate = FakeTrace(isTraceable, isCurrentlyTracing)

  // These aren't part of TraceInterface
  val traceCalls: List<FakeTrace.TraceCall> get() = delegate.traceCalls
  fun clearTraceCalls() = delegate.clearTraceCalls()

  override val isTraceable: Boolean get() = delegate.isTraceable
  override val isCurrentlyTracing: Boolean get() = delegate.isCurrentlyTracing

  override fun beginSection(label: String) = delegate.beginSection(label)
  override fun endSection() = delegate.endSection()
  override fun beginAsyncSection(
    name: String,
    cookie: Int
  ) = delegate.beginAsyncSection(name, cookie)

  override fun endAsyncSection(
    name: String,
    cookie: Int
  ) = delegate.endAsyncSection(name, cookie)

  override fun logSection(info: String) = delegate.logSection(info)
}
