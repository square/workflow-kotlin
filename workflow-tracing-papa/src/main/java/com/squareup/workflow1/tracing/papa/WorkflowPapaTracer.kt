package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.tracing.TraceInterface
import com.squareup.workflow1.tracing.WorkflowTrace
import com.squareup.workflow1.tracing.WorkflowTracer

@Deprecated(
  message = "Renamed to WorkflowTracer and moved to com.squareup.workflow1.tracing package",
  replaceWith = ReplaceWith(
    expression = "WorkflowTracer",
    imports = arrayOf("com.squareup.workflow1.tracing.WorkflowTracer")
  )
)
class WorkflowPapaTracer(
  safeTrace: TraceInterface = WorkflowTrace(isTraceable = false)
) : WorkflowTracer(safeTrace)
