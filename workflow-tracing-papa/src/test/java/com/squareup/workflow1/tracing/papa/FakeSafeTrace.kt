package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.SafeTraceInterface

/**
 * Fake implementation of [SafeTraceInterface] for testing purposes.
 * Records all trace calls for verification in tests.
 */
class FakeSafeTrace(
  override val isTraceable: Boolean = true,
  override val isCurrentlyTracing: Boolean = true
) : SafeTraceInterface {

  data class TraceCall(
    val type: String,
    val label: String? = null,
    val name: String? = null,
    val cookie: Int? = null
  )

  private val _traceCalls = mutableListOf<TraceCall>()
  val traceCalls: List<TraceCall> get() = _traceCalls.toList()

  fun clearTraceCalls() {
    _traceCalls.clear()
  }

  override fun beginSection(label: String) {
    _traceCalls.add(TraceCall("beginSection", label = label))
  }

  override fun endSection() {
    _traceCalls.add(TraceCall("endSection"))
  }

  override fun beginAsyncSection(
    name: String,
    cookie: Int
  ) {
    _traceCalls.add(TraceCall("beginAsyncSection", name = name, cookie = cookie))
  }

  override fun endAsyncSection(
    name: String,
    cookie: Int
  ) {
    _traceCalls.add(TraceCall("endAsyncSection", name = name, cookie = cookie))
  }

  override fun logSection(info: String) {
    _traceCalls.add(TraceCall("logSection", label = info))
  }
}
