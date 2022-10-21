package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowRunner
import com.squareup.workflow1.id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * @see [WorkflowRunner]. This version supports running with the Compose runtime optimizations.
 */
@WorkflowExperimentalRuntime
public class WorkflowComposeRunner<PropsT, OutputT, RenderingT>(
  scope: CoroutineScope,
  protoWorkflow: Workflow<PropsT, OutputT, RenderingT>,
  props: StateFlow<PropsT>,
  snapshot: TreeSnapshot?,
  interceptor: ComposeWorkflowInterceptor,
  runtimeConfig: RuntimeConfig
) : WorkflowRunner<PropsT, OutputT, RenderingT>(
  scope,
  protoWorkflow,
  props,
  snapshot,
  interceptor,
  runtimeConfig,
) {

  override var currentProps: PropsT by mutableStateOf(props.value)

  override val rootNode: WorkflowComposeNode<PropsT, out Any?, OutputT, RenderingT> =
    WorkflowComposeNode(
      id = workflow.id(),
      workflow = workflow.asComposeWorkflow(),
      initialProps = currentProps,
      snapshot = snapshot,
      baseContext = scope.coroutineContext,
      interceptor = interceptor,
      idCounter = idCounter
    ).apply {
      startSession()
    }

  @Composable
  public fun nextComposedRendering(): RenderingAndSnapshot<RenderingT> {
    val rendering = remember { mutableStateOf<RenderingT?>(null) }

    rootNode.Rendering(workflow as StatefulComposeWorkflow, currentProps, rendering)

    val snapshot = remember(workflow) {
      // need to key this on state inside WorkflowNode. Likely have a Compose version.
      rootNode.snapshot(workflow)
    }

    return RenderingAndSnapshot(rendering.value!!, snapshot)
  }
}
