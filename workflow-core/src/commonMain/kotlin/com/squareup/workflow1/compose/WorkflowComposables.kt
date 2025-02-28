package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Workflow

/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a [ComposeWorkflow.Rendering] or
 * [BaseRenderContext.renderComposable]) and returns its rendering.
 *
 * @param onOutput An optional function that, if non-null, will be called when the child emits an
 * output. If null, the child's outputs will be ignored.
 */
@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
  workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT {
  val host = LocalWorkflowCompositionHost.current
  return host.renderChild(workflow, props, onOutput)
}

/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a [ComposeWorkflow.Rendering] or
 * [BaseRenderContext.renderComposable]) and returns its rendering.
 *
 * @param onOutput An optional function that, if non-null, will be called when the child emits an
 * output. If null, the child's outputs will be ignored.
 */
@WorkflowComposable
@Composable
inline fun <ChildPropsT, ChildRenderingT> renderChild(
  workflow: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
): ChildRenderingT = renderChild(workflow, props, onOutput = null)

/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a [ComposeWorkflow.Rendering] or
 * [BaseRenderContext.renderComposable]) and returns its rendering.
 *
 * @param onOutput An optional function that, if non-null, will be called when the child emits an
 * output. If null, the child's outputs will be ignored.
 */
@WorkflowComposable
@Composable
inline fun <ChildOutputT, ChildRenderingT> renderChild(
  workflow: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  noinline onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT = renderChild(workflow, props = Unit, onOutput)

/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a [ComposeWorkflow.Rendering] or
 * [BaseRenderContext.renderComposable]) and returns its rendering.
 *
 * @param onOutput An optional function that, if non-null, will be called when the child emits an
 * output. If null, the child's outputs will be ignored.
 */
@WorkflowComposable
@Composable
inline fun <ChildRenderingT> renderChild(
  workflow: Workflow<Unit, Nothing, ChildRenderingT>,
): ChildRenderingT = renderChild(workflow, Unit, onOutput = null)

@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
  workflow: ComposeWorkflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  handler: ((ChildOutputT) -> Unit)?
): ChildRenderingT {
  val childRendering = remember { mutableStateOf<ChildRenderingT?>(null) }
  // Since this function returns a value, it can't restart without also restarting its parent.
  // IsolateRecomposeScope allows the subtree to restart and only restarts us if the rendering value
  // actually changed.
  RecomposeScopeIsolator(
    child = workflow,
    props = props,
    handler = handler,
    result = childRendering
  )
  @Suppress("UNCHECKED_CAST")
  return childRendering.value as ChildRenderingT
}

@Composable
private fun <PropsT, OutputT, RenderingT> RecomposeScopeIsolator(
  child: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  handler: ((OutputT) -> Unit)?,
  result: MutableState<RenderingT>,
) {
  result.value = child.Rendering(props, handler ?: {})
}
