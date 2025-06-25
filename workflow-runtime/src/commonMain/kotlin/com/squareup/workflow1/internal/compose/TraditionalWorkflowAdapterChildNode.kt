package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.AbstractWorkflowNode
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.createWorkflowNode
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

internal class TraditionalWorkflowAdapterChildNode<PropsT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  contextForChildren: CoroutineContext,
  parent: WorkflowSession?,
  snapshot: TreeSnapshot?,
  workflowTracer: WorkflowTracer?,
  runtimeConfig: RuntimeConfig,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter?,
  acceptChildActionResult: (ActionApplied<OutputT>) -> ActionProcessingResult,
) : ComposeChildNode<PropsT, OutputT, RenderingT> {

  private val workflowNode: AbstractWorkflowNode<PropsT, OutputT, RenderingT> = createWorkflowNode(
    id = id,
    workflow = workflow,
    initialProps = initialProps,
    snapshot = snapshot,
    baseContext = contextForChildren,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    emitAppliedActionToParent = acceptChildActionResult,
    parent = parent,
    interceptor = interceptor,
    idCounter = idCounter
  )

  override val id: WorkflowNodeId
    get() = workflowNode.id

  @Composable
  override fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT = workflowNode.render(
    workflow = workflow,
    input = props
  )

  override fun snapshot(): TreeSnapshot = workflowNode.snapshot()

  override fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean =
    workflowNode.onNextAction(selector)
}
