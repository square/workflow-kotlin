package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.internal.WorkflowNodeId
import kotlinx.coroutines.selects.SelectBuilder

internal interface ComposeChildNode<PropsT, OutputT, RenderingT> {

  val id: WorkflowNodeId

  @Composable fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT

  fun snapshot(): TreeSnapshot

  fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun rememberChildRenderKey(): String {
  return currentCompositeKeyHash.toHexString(HexFormat.Default)
}
