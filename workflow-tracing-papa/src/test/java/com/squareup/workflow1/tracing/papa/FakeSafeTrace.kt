package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.FakeTrace
import com.squareup.workflow1.tracing.TraceInterface

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
) : TraceInterface by FakeTrace(isTraceable, isCurrentlyTracing) {
  private val delegate = FakeTrace(isTraceable, isCurrentlyTracing)

  // These aren't part of TraceInterface
  val traceCalls: List<FakeTrace.TraceCall> get() = delegate.traceCalls
  fun clearTraceCalls() = delegate.clearTraceCalls()
}
