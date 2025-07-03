package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.internal.AbstractWorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import kotlinx.coroutines.selects.SelectBuilder

/**
 * Represents a workflow inside a [ComposeWorkflowNodeAdapter], either another
 * [ComposeWorkflow]/[ComposeWorkflowChildNode], or a
 * [Workflow]/[TraditionalWorkflowAdapterChildNode].
 */
internal interface ComposeChildNode<PropsT, OutputT, RenderingT> {

  /** See [AbstractWorkflowNode.id]. */
  val id: WorkflowNodeId

  /**
   * Called during workflow render passes to produce the rendering for this workflow.
   *
   * The compose analog to [AbstractWorkflowNode.render].
   */
  @Composable fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT

  /** See [AbstractWorkflowNode.snapshot]. */
  fun snapshot(): TreeSnapshot

  /** See [AbstractWorkflowNode.onNextAction]. */
  fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean
}

/**
 * Returns a stable key identifying a call to [ComposeWorkflowChildNode.renderChild] in the
 * composition, suitable for use with [WorkflowIdentifier].
 */
@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun rememberChildRenderKey(): String {
  return currentCompositeKeyHash.toHexString(HexFormat.Default)
}
