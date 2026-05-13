package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME_SKIPPING
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.compose.ComposeRenderContext.Companion.rememberComposeRenderContext
import com.squareup.workflow1.renderWorkflowIn

internal val LocalRootRecomposeScope = staticCompositionLocalOf<RecomposeScope> { error("Not set") }

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
@Suppress("UNCHECKED_CAST")
@OptIn(WorkflowExperimentalRuntime::class)
// @ExplicitGroupsComposable
@Composable
internal fun <PropsT, OutputT, RenderingT> renderWorkflow(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
  recomposeScope: RecomposeScope = currentRecomposeScope,
): RenderingT {
  // The lifetime of the workflow session is tied to the workflow.identifier, but we don't key on it
  // here since it's already keyed from ComposeRenderContext.

  // Skip re-rendering when possible, but force recompose when new props or onOutput arrive.
  // We use the skippable+restartable variant so internal state-change invalidations trigger a fresh
  // call to the producer lambda within the same restart group.
  return if (COMPOSE_RUNTIME_SKIPPING in config.runtimeConfig) {
    return renderWorkflowRestartableImpl(
      workflow = workflow,
      props = props,
      onOutput = onOutput as ((Any?) -> Unit)?,
      config = config,
      parentSession = parentSession,
      renderKey = renderKey,
      invalidateCallerOnNewValue = false,
      // Note: If this is recomposeScope, we'll invalidate the entire path up the tree and recompose
      // in one go. If it's currentRecomposeScope, we'll trampoline. Benchmarks show that doing a
      // single recompose pass is roughly twice as fast as trampolining in some cases, so we do that
      // as an optimization.
      callerRecomposeScope = recomposeScope,
      composer = currentComposer,
      changed = 0
    ) as RenderingT
  } else {
    renderWorkflowImpl(
      workflow as Workflow<Any?, Any?, Any?>,
      props,
      onOutput as ((Any?) -> Unit)?,
      config,
      parentSession,
      renderKey,
      recomposeScope,
    ) as RenderingT
  }
}

private val renderWorkflowImpl = @Composable fun(
  workflow: Workflow<Any?, Any?, Any?>,
  props: Any?,
  onOutput: ((Any?) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
  recomposeScope: RecomposeScope,
): Any? {
  val baseContext = rememberComposeRenderContext(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    config = config,
    parentSession = parentSession,
    renderKey = renderKey,
    callerRecomposeScope = recomposeScope,
  )

  // TODO this feels weird to have outside the context, should it be moved in too? Should this
  //  whole function be moved into the context? I think unit tests will be the only real forcing
  //  function, so let's write some tests and see how it works.
  return baseContext.renderSelf(props)
}

@Suppress("USELESS_CAST", "UNCHECKED_CAST")
private val renderWorkflowImplComposable = renderWorkflowImpl as (
  Workflow<*, *, *>,
  Any?,
  ((Any?) -> Unit)?,
  WorkflowComposableRuntimeConfig,
  WorkflowSession?,
  String,
  RecomposeScope,
  Composer,
  Int
) -> Any?

@Suppress("UNCHECKED_CAST")
private fun renderWorkflowRestartableImpl(
  workflow: Workflow<*, *, *>,
  props: Any?,
  onOutput: ((Any?) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
  invalidateCallerOnNewValue: Boolean,
  callerRecomposeScope: RecomposeScope,
  composer: Composer,
  changed: Int
): Any? {
  // Outer group is restartable: This should wrap the entire body of this function (except the actual
  // return statement) and is what defines the recompose scope for producer.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startRestartGroup(23975234)

  // Only gets set if we end up composing producer this invocation.
  var newValue: Any? = Composer.Empty

  // region Recompose producer
  // Inner group is necessary to be able to skip calling producer. We need a nested group because we
  // only want to skip calling producer, we still need to do other slot table stuff later to
  // read the cache even if producer is skipped.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startReplaceGroup(-895982)

  // Many parameters to this function will never change between recompositions so we don't need to
  // check them here.
  val arg1 = workflow
  val arg2 = props
  val arg3 = onOutput
  var dirty = changed
  if ((changed and 0b110) == 0) {
    dirty = changed or (if (composer.changed(arg1)) 0b100 else 0b010)
  }
  if ((changed and 0b110_000) == 0) {
    dirty = dirty or (if (composer.changed(arg2)) 0b100_000 else 0b010_000)
  }
  if ((changed and 0b110_000_000) == 0) {
    dirty = dirty or (if (composer.changed(arg3)) 0b100_000_000 else 0b010_000_000)
  }
  if ((dirty and 0b010_010_011) == 0b010_010_010 && composer.skipping) {
    composer.skipToGroupEnd()
  } else {
    newValue = renderWorkflowImplComposable(
      workflow,
      props,
      onOutput,
      config,
      parentSession,
      renderKey,
      callerRecomposeScope,
      composer,
      // changed: We can tell it exactly what changed. Each group of three corresponds to a param,
      // with the rightmost group being the first parameter (workflow).
      // TODO remember if props/onOutput changed from above logic, pass those flags.
      // TODO use this value to avoid re-comparing inside this function.
      0b010_010_010_000_000_000
    )
  }

  composer.endReplaceGroup()
  // endregion

  // region Update cache
  // Cache the return value in case we skipped above. Composer APIs require always reading the value
  // first, and then calling updateRememberedValue the first time or optionally on subsequent
  // recompositions. Identity comparison is intentional: the values cached here may be workflow
  // renderings whose `equals` is allowed to throw or have side effects, so we must never call
  // `equals` on them. Skipping decisions are already driven by composer.changed() on the keys
  // above; the only remaining job here is "did the producer run? if so, take its output".
  val oldValue = composer.rememberedValue()
  val returnValue =
    if (oldValue !== Composer.Empty && (newValue === Composer.Empty || newValue === oldValue)) {
    // Producer was skipped, return from the cache.
    oldValue
  } else {
    // Producer ran, update the cache and return its new value.
    composer.updateRememberedValue(newValue)

    // When we're recomposed directly, we obviously can't return returnValue to the original caller,
    // so just invalidate it instead. It will eventually recompose after we're done in the same frame,
    // and when it does so it should hit the cache (unless the caller passes a new producer).
    if (invalidateCallerOnNewValue) {
      callerRecomposeScope.invalidate()
    }
    newValue
  }
  // endregion

  composer.endRestartGroup()?.updateScope { composer, changed ->
    // This lambda is called when producer is invalidated. The lambda must create a restartable
    // group with the same key to preserve positional identity.
    renderWorkflowRestartableImpl(
      workflow = workflow,
      props = props,
      onOutput = onOutput,
      config = config,
      parentSession = parentSession,
      renderKey = renderKey,
      invalidateCallerOnNewValue = true,
      callerRecomposeScope = callerRecomposeScope,
      composer = composer,
      changed = changed
    )
  }
  return returnValue
}
