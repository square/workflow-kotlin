package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.TraceInterface
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
) : TraceInterface by WorkflowTrace(isTraceable)
