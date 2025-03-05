package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi

/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a
 * [ComposeWorkflow.produceRendering] or [BaseRenderContext.renderComposable]) and returns its
 * rendering.
 *
 * This method supports rendering any [Workflow] type, including [ComposeWorkflow]s. If [workflow]
 * is a [ComposeWorkflow] then it is composed directly without a detour to the traditional workflow
 * system.
 *
 * Note that there's no `key` parameter: Child workflows are keyed by their position (where they're
 * called from) in the composition and the identity of the workflow itself, in the same way that
 * composables themselves are keyed.
 *
 * @param onOutput An optional function that, if non-null, will be called when the child emits an
 * output. If null, the child's outputs will be ignored.
 */
// TODO should these be extension functions?
@WorkflowExperimentalApi
@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderWorkflow(
  workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT =
// We need to key on workflow so that all the state associated with the workflow is moved or removed
// with that particular instance of the workflow. E.g. if a single renderWorkflow call is passed
// a workflow from props, and the workflow changes, then all the state from the old session should
// be removed and replaced with completely new state for the new workflow. This matches how normal
  // renderChild calls work.
  key(workflow) {
    if (workflow is ComposeWorkflow) {
      // Don't need to jump out into non-workflow world if the workflow is already composable.
      workflow.renderWithRecomposeBoundary(props, onOutput)
    } else {
      val host = LocalWorkflowCompositionHost.current
      host.renderChild(workflow, props, onOutput)
    }
  }

// region Convenience overloads for specific type arguments

/**
 * Renders a child [Workflow] that has no output (`OutputT` is [Nothing]).
 * For more documentation see [renderWorkflow].
 */
@WorkflowExperimentalApi
@WorkflowComposable
@Composable
inline fun <ChildPropsT, ChildRenderingT> renderWorkflow(
  workflow: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
): ChildRenderingT = renderWorkflow(workflow, props, onOutput = null)

/**
 * Renders a child [Workflow] that has no props (`PropsT` is [Unit]).
 * For more documentation see [renderWorkflow].
 */
@WorkflowExperimentalApi
@WorkflowComposable
@Composable
inline fun <ChildOutputT, ChildRenderingT> renderWorkflow(
  workflow: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  noinline onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT = renderWorkflow(workflow, props = Unit, onOutput)

/**
 * Renders a child [Workflow] that has no props or output (`PropsT` is [Unit], `OutputT` is
 * [Nothing]).
 * For more documentation see [renderWorkflow].
 */
@WorkflowExperimentalApi
@WorkflowComposable
@Composable
inline fun <ChildRenderingT> renderWorkflow(
  workflow: Workflow<Unit, Nothing, ChildRenderingT>,
): ChildRenderingT = renderWorkflow(workflow, Unit, onOutput = null)

// endregion
