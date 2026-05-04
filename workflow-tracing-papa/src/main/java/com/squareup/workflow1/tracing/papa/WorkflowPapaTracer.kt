@file:Suppress("DEPRECATION")

package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.SafeTraceInterface
import com.squareup.workflow1.tracing.TraceInterface
import com.squareup.workflow1.tracing.WorkflowTracer

@Deprecated(
  message = "Renamed to WorkflowTracer and moved to com.squareup.workflow1.tracing package",
  replaceWith = ReplaceWith(
    expression = "WorkflowTracer",
    imports = arrayOf("com.squareup.workflow1.tracing.WorkflowTracer")
  )
)
class WorkflowPapaTracer(
  safeTrace: SafeTraceInterface = PapaSafeTrace(isTraceable = false)
) : WorkflowTracer(safeTrace) {
  constructor(safeTrace: TraceInterface) : this(safeTrace.asSafeTraceInterface())

  companion object
}

private fun TraceInterface.asSafeTraceInterface(): SafeTraceInterface {
  return this as? SafeTraceInterface ?: object : SafeTraceInterface {
    override val isTraceable: Boolean get() = this@asSafeTraceInterface.isTraceable
    override val isCurrentlyTracing: Boolean get() = this@asSafeTraceInterface.isCurrentlyTracing

    override fun beginSection(label: String) = this@asSafeTraceInterface.beginSection(label)
    override fun endSection() = this@asSafeTraceInterface.endSection()
    override fun beginAsyncSection(
      name: String,
      cookie: Int
    ) = this@asSafeTraceInterface.beginAsyncSection(name, cookie)

    override fun endAsyncSection(
      name: String,
      cookie: Int
    ) = this@asSafeTraceInterface.endAsyncSection(name, cookie)

    override fun logSection(info: String) = this@asSafeTraceInterface.logSection(info)
  }
}
