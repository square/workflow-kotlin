@file:Suppress("DEPRECATION")

package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.SafeTraceInterface
import com.squareup.workflow1.tracing.WorkflowTrace

@Deprecated(
  message = "Renamed to WorkflowTrace and moved to com.squareup.workflow1.tracing package",
  replaceWith = ReplaceWith(
    expression = "WorkflowTrace",
    imports = arrayOf("com.squareup.workflow1.tracing.WorkflowTrace")
  )
)
class PapaSafeTrace(
  isTraceable: Boolean = false
) : SafeTraceInterface {
  private val delegate = WorkflowTrace(isTraceable)

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
