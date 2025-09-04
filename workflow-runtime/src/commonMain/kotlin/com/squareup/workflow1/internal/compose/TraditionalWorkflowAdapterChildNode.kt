package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.createWorkflowNode
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

/**
 * Entry point back into the Workflow runtime from a Compose runtime (i.e. a
 * [ComposeWorkflowNodeAdapter]).
 */
internal class TraditionalWorkflowAdapterChildNode<PropsT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  contextForChildren: CoroutineContext,
  private val parentNode: ComposeWorkflowChildNode<*, *, *>?,
  parent: WorkflowSession?,
  snapshot: TreeSnapshot?,
  workflowTracer: WorkflowTracer?,
  runtimeConfig: RuntimeConfig,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter?,
  acceptChildActionResult: (ActionApplied<OutputT>) -> ActionProcessingResult,
) : ComposeChildNode<PropsT, OutputT, RenderingT>,
  RememberObserver {

  private val workflowNode: WorkflowNode<PropsT, OutputT, RenderingT> = createWorkflowNode(
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

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    workflowNode.registerTreeActionSelectors(selector)
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult =
    workflowNode.applyNextAvailableTreeAction(skipDirtyNodes)

  /**
   * Track child nodes for snapshotting.
   * NOTE: While the effect will run after composition, it will run as part of the compose
   * frame, so the child will be registered before ComposeWorkflowNodeAdapter's render method
   * returns.
   */
  override fun onRemembered() {
    parentNode?.addChildNode(this)
  }

  override fun onForgotten() {
    parentNode?.removeChildNode(this)
  }

  override fun onAbandoned() = Unit
}
