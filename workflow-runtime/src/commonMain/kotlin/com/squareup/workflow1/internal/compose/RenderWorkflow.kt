package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExplicitGroupsComposable
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentRecomposeScope
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME_SKIPPING
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
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
@OptIn(WorkflowExperimentalRuntime::class)
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
  return if (COMPOSE_RUNTIME_SKIPPING in config.runtimeConfig) {
    @Suppress("UNCHECKED_CAST")
    return renderWorkflowRestartableImplComposable(
      workflow,
      props,
      onOutput as ((Any?) -> Unit)?,
      config,
      parentSession,
      renderKey,
      false, // invalidateOnNewValue
      currentRecomposeScope,
    ) as RenderingT
  } else {
    renderWorkflowImpl(
      workflow,
      props,
      onOutput,
      config,
      parentSession,
      renderKey,
    )
  }
}

@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun <PropsT, OutputT, RenderingT> renderWorkflowImpl(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  noinline onOutput: ((OutputT) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
): RenderingT {
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
  return baseContext.renderSelf(props)
}

@Suppress("USELESS_CAST", "UNCHECKED_CAST")
private val renderWorkflowImplErased = @ExplicitGroupsComposable @Composable fun(
  workflow: Workflow<Any?, Any?, Any?>,
  props: Any?,
  onOutput: ((Any?) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
): Any? {
  return renderWorkflowImpl(
    workflow = workflow,
    props = props as Any?,
    onOutput = onOutput,
    config = config,
    parentSession = parentSession,
    renderKey = renderKey,
  )
} as (
  Workflow<*, *, *>,
  Any?,
  ((Any?) -> Unit)?,
  WorkflowComposableRuntimeConfig,
  WorkflowSession?,
  String,
  Composer,
  Int
) -> Any?

@Suppress("UNCHECKED_CAST")
private val renderWorkflowRestartableImpl = fun(
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
  val arg1 = props
  val arg2 = onOutput
  var dirty = changed
  if ((changed and 0b110) == 0) {
    dirty = changed or (if (composer.changed(arg1)) 0b100 else 0b010)
  }
  if ((changed and 0b110_000) == 0) {
    dirty = dirty or (if (composer.changed(arg2)) 0b100_000 else 0b010_000)
  }
  // if ((changed and 0b110_000_000) == 0) {
  //   dirty = dirty or (if (composer.changed(arg3)) 0b100_000_000 else 0b010_000_000)
  // }
  if ((dirty and 0b010_011) == 0b010_010 && composer.skipping) {
    composer.skipToGroupEnd()
  } else {
    newValue = renderWorkflowImplErased(
      workflow,
      props,
      onOutput,
      config,
      parentSession,
      renderKey,
      composer,
      0
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
  val returnValue = if (newValue === Composer.Empty) {
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
    restartRenderWorkflowRestartableImpl(
      workflow = workflow,
      props = props,
      onOutput = onOutput,
      config = config,
      parentSession = parentSession,
      renderKey = renderKey,
      callerRecomposeScope = callerRecomposeScope,
      composer = composer,
      changed = changed
    )
  }
  return returnValue
}

@Suppress("UNCHECKED_CAST")
private val renderWorkflowRestartableImplComposable = renderWorkflowRestartableImpl as @Composable (
  Workflow<*, *, *>,
  Any?,
  ((Any?) -> Unit)?,
  WorkflowComposableRuntimeConfig,
  WorkflowSession?,
  String,
  Boolean,
  RecomposeScope,
) -> Any?

@Suppress("UNCHECKED_CAST")
private fun restartRenderWorkflowRestartableImpl(
  workflow: Workflow<*, *, *>,
  props: Any?,
  onOutput: ((Any?) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
  callerRecomposeScope: RecomposeScope,
  composer: Composer,
  changed: Int
): Any? = renderWorkflowRestartableImpl.invoke(
  workflow,
  props,
  onOutput,
  config,
  parentSession,
  renderKey,
  true, // invalidateCallerOnNewValue
  callerRecomposeScope,
  composer,
  changed
)
