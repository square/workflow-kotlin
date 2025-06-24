package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

internal class ComposeWorkflowNode<PropsT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  // workflow: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
) : AbstractWorkflowNode<PropsT, OutputT, RenderingT>(
  id = id,
  runtimeConfig = runtimeConfig,
  workflowTracer = workflowTracer,
  parent = parent,
  baseContext = baseContext,
  idCounter = idCounter,
  interceptor = interceptor,
  emitAppliedActionToParent = emitAppliedActionToParent,
) {

  init {
    interceptor.onSessionStarted(workflowScope = this, session = this)
  }

  override fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT {
    TODO("Not yet implemented")
  }

  override fun snapshot(): TreeSnapshot {
    TODO("Not yet implemented")
  }

  override fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean {
    TODO("Not yet implemented")
  }

  override fun cancel(cause: CancellationException?) {
    TODO("Not yet implemented")
  }
}
