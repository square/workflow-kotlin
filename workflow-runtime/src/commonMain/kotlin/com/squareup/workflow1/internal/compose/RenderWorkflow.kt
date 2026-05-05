package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.compose.ComposeRenderContext.Companion.rememberComposeRenderContext
import com.squareup.workflow1.renderWorkflowIn

/**
 * This is the entry point for hosting a workflow tree inside a composition. It manages all the
 * bookkeeping for the workflow session. It's analogous to [WorkflowNode] in the traditional
 * runtime.
 *
 * It is called from at least two places:
 *  - The root of the compose workflow runtime, from [renderWorkflowIn].
 *  - Any time a workflow renders a child (see [ComposeRenderContext]).
 *
 * In the future, it could potentially become public API for rendering child workflows from
 * workflows that are written as actual composable functions, but exposing it publicly would require
 * some additional work to ensure it can't be called incorrectly (ensuring [config] doesn't change,
 * hiding [parentSession], keying on `workflow.identifier`, etc.)
 *
 * @param config Workflow-tree-wide configuration that must never change during the lifetime of the
 * runtime. This is not currently enforced because doing so would incur some overhead in the slot
 * table, but behavior is undefined if it does change.
 * @param renderKey The key passed to the [com.squareup.workflow1.BaseRenderContext.renderChild]
 * function by the parent workflow. This is only used to construct the child's [WorkflowSession],
 * and is not used for actual keying. [ComposeRenderContext] does the actual keying.
 */
@Composable
internal fun <PropsT, OutputT, RenderingT> renderWorkflow(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
): RenderingT {
  // The lifetime of the workflow session is tied to the workflow.identifier, but we don't key on it
  // here since it's already keyed from ComposeRenderContext.

  // Skip re-rendering when possible, but force recompose when new props or onOutput arrive.
  // We use the skippable+restartable variant so internal state-change invalidations trigger a fresh
  // call to the producer lambda within the same restart group.
  //
  // Notes on the previous TODOs (now resolved by enabling this path):
  //  - Plain "skipping without restarting" can't work for us: we have no access to the compiler-
  //    generated $changed parameter, so we can't tell whether stale-but-equal keys mean we should
  //    skip. The restartable variant sidesteps this by giving each call its own restart group whose
  //    invalidation is what we already drive from action application.
  //  - The "trampolining" cost is one captured composable lambda per renderWorkflow call. The
  //    benchmark deltas justify that allocation.
  return rememberSkippableAndRestartableComposable(key1 = props, key2 = onOutput) {
    val baseContext = rememberComposeRenderContext(
      workflow = workflow,
      props = props,
      onOutput = onOutput,
      config = config,
      parentSession = parentSession,
      renderKey = renderKey,
    )

    // TODO this feels weird to have outside the context, should it be moved in too? Should this
    //  whole function be moved into the context? I think unit tests will be the only real forcing
    //  function, so let's write some tests and see how it works.
    baseContext.renderSelf(props)
  }
}
