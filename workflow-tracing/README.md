# Module workflow-tracing

This module provides the [com.squareup.workflow1.WorkflowRuntimeMonitor] which is a special
[com.squareup.workflow1.WorkflowRuntimeInterceptor] that can be used to monitor an entire runtime
on Android with bookkeeping about the workflow sessions that enables intelligent tracing of all
Workflows and the runtime. Those can be added optionally by passing in custom
[com.squareup.workflow.WorkflowRuntimeTracer]s.
