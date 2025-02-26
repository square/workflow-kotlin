package com.squareup.workflow1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalWorkflowComposableRenderer =
  staticCompositionLocalOf<WorkflowComposableRenderer> { error("No renderer") }

internal interface WorkflowComposableRenderer {
  @Composable
  fun <ChildPropsT, ChildOutputT, ChildRenderingT> Child(
    workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    onOutput: ((ChildOutputT) -> Unit)?
  ): ChildRenderingT
}

/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a [ComposeWorkflow.Rendering] or
 * [BaseRenderContext.renderComposable]) and returns its rendering.
 *
 * @param handler An optional function that, if non-null, will be called when the child emits an
 * output. If null, the child's outputs will be ignored.
 */
@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> Child(
  workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT {
  val renderer = LocalWorkflowComposableRenderer.current
  return renderer.Child(workflow, props, onOutput)
}

@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> Child(
  workflow: ComposeWorkflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  handler: ((ChildOutputT) -> Unit)?
): ChildRenderingT {
  val childRendering = remember { mutableStateOf<ChildRenderingT?>(null) }
  // Since this function returns a value, it can't restart without also restarting its parent.
  // IsolateRecomposeScope allows the subtree to restart and only restarts us if the rendering value
  // actually changed.
  IsolateRecomposeScope(
    child = workflow,
    props = props,
    handler = handler,
    result = childRendering
  )
  @Suppress("UNCHECKED_CAST")
  return childRendering.value as ChildRenderingT
}

@Composable
private fun <PropsT, OutputT, RenderingT> IsolateRecomposeScope(
  child: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  handler: ((OutputT) -> Unit)?,
  result: MutableState<RenderingT>,
) {
  result.value = child.Rendering(props, handler ?: {})
}
