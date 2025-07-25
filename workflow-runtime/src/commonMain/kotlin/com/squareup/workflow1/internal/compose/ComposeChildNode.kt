package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import kotlinx.coroutines.selects.SelectBuilder

/**
 * Represents a workflow inside a [ComposeWorkflowNodeAdapter], either another
 * [ComposeWorkflow]/[ComposeWorkflowChildNode], or a
 * [Workflow]/[TraditionalWorkflowAdapterChildNode].
 */
internal interface ComposeChildNode<PropsT, OutputT, RenderingT> {

  /** See [WorkflowNode.id]. */
  val id: WorkflowNodeId

  /**
   * Called during workflow render passes to produce the rendering for this workflow.
   *
   * The compose analog to [WorkflowNode.render].
   */
  @Composable fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT

  /** See [WorkflowNode.snapshot]. */
  fun snapshot(): TreeSnapshot

  /** See [WorkflowNode.registerTreeActionSelectors]. */
  fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>)

  /** See [WorkflowNode.applyNextAvailableTreeAction]. */
  fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean = false): ActionProcessingResult
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
