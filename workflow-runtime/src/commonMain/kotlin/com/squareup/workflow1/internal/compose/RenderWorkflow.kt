package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.ComposeRuntimeSwizzlerWorkflow
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
 * - The root of the compose workflow runtime, from [renderWorkflowIn].
 * - Any time a workflow renders a child (see [ComposeRenderContext]).
 *
 * In the future, it could potentially become public API for rendering child workflows from
 * workflows that are written as actual composable functions, but exposing it publicly would require
 * some additional work to ensure it can't be called incorrectly (ensuring [config] doesn't change,
 * hiding [parentSession], keying on `workflow.identifier`, etc.)
 *
 * @param config Workflow-tree-wide configuration that must never change during the lifetime of the
 *   runtime. This is not currently enforced because doing so would incur some overhead in the slot
 *   table, but behavior is undefined if it does change.
 * @param renderKey The key passed to the [com.squareup.workflow1.BaseRenderContext.renderChild]
 *   function by the parent workflow. This is only used to construct the child's [WorkflowSession],
 *   and is not used for actual keying. [ComposeRenderContext] does the actual keying.
 * @param onSessionAvailable Invoked once (per callback instance) with the [WorkflowSession] of this
 * workflow once it's been created.
 */
@Suppress("UNCHECKED_CAST")
@OptIn(WorkflowExperimentalRuntime::class)
@Composable
internal fun <PropsT, OutputT, RenderingT> renderWorkflow(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  renderKey: String,
  recomposeScope: RecomposeScope = currentRecomposeScope,
  onSessionAvailable: ((WorkflowSession) -> Unit)? = null,
): RenderingT {
  if (workflow is ComposeRuntimeSwizzlerWorkflow<PropsT, OutputT, RenderingT>) {
    // This workflow is just a wrapper telling us to switch to the compose runtime. Since we're
    // already in that runtime, just render its child directly.
    return renderWorkflow(
      workflow = workflow.child,
      props = props,
      onOutput = onOutput,
      config = config,
      parentSession = parentSession,
      renderKey = renderKey,
      onSessionAvailable = onSessionAvailable,
    )
  }

  // The lifetime of the workflow session is tied to the workflow.identifier, but we don't key on it
  // here since it's already keyed from ComposeRenderContext.
  val renderContext =
    rememberComposeRenderContext(
      workflow = workflow,
      initialProps = props,
      config = config,
      parentSession = parentSession,
      renderKey = renderKey,
      callerRecomposeScope = recomposeScope,
    )

  if (onSessionAvailable != null) {
    DisposableEffect(onSessionAvailable, renderContext) {
      onSessionAvailable(renderContext)
      onDispose {}
    }
  }

  // Skip re-rendering when possible, but force recompose when new props or onOutput arrive.
  // We use the skippable+restartable variant so internal state-change invalidations trigger a fresh
  // call to the producer lambda within the same restart group.
  return if (COMPOSE_RUNTIME_SKIPPING in config.runtimeConfig) {
    renderWorkflowRestartable(
      props,
      onOutput as ((Any?) -> Unit)?,
      renderContext,
      false,
      recomposeScope,
    )
      as RenderingT
  } else {
    renderContext.updateRecomposeScope(currentRecomposeScope)
    renderContext.renderSelf(
      props = props,
      onOutput = onOutput as ((Any?) -> Unit)?,
      didPropsChange = null,
      didOnOutputChange = null,
      composer = currentComposer,
    )
  }
}

/**
 * Exposes [renderWorkflowRestartableImpl] as a composable function, to get the compiler to wire up
 * the `changed` flags.
 */
@Suppress("UNCHECKED_CAST")
private val renderWorkflowRestartable =
  ::renderWorkflowRestartableImpl
    as
    @Composable
    (Any?, ((Any?) -> Unit)?, ComposeRenderContext<*, *, *>, Boolean, RecomposeScope) -> Any?

@OptIn(InternalComposeApi::class)
@Suppress("NAME_SHADOWING")
private fun renderWorkflowRestartableImpl(
  props: Any?,
  onOutput: ((Any?) -> Unit)?,
  renderContext: ComposeRenderContext<*, *, *>,
  invalidateCallerOnNewValue: Boolean,
  callerRecomposeScope: RecomposeScope,
  composer: Composer,
  changed: Int,
): Any? {
  // Outer group is restartable: This should wrap the entire body of this function (except the
  // actual
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

  // The changed parameter is a bit set. Each parameter gets 3 bits, starting with the LSB for the
  // first parameter. In each set of three:
  //   - the LSB is ignored (for now),
  //   - the middle bit being set (0b010) means the arg is already known to NOT have changed since
  //     the last call, and
  //   - the MSB being set (0b100) means the arg is known TO HAVE definitely changed since the last
  //     call.
  // If no bits are set, that means the caller hasn't told us (probably doesn't know) whether the
  // arg has changed, so we need to ask the composer to check for us.
  // The least-significant bit of the parameter is special: if it's set that means force recompose
  // no matter what. This is used when the function is invalidated explicitly, and no parameters or
  // state (composer.skipping) have changed.
  // Note that it *is* possible for both bits to be set (0b110). I'm not sure what this means
  // exactly, but we can infer from the generated code that Compose treats it as meaning it's
  // skippable.
  //
  // Many parameters to this function will never change between recompositions so we don't need to
  // check them here.
  var dirty = changed
  if ((changed and 0b110) == 0) {
    dirty = dirty or (if (composer.changed(props)) 0b100 else 0b010)
  }
  if ((changed and 0b110_000) == 0) {
    dirty = dirty or (if (composer.changed(onOutput)) 0b100_000 else 0b010_000)
  }
  // We only check props and onOutput since the other parameters cannot change in a composition.

  if ((dirty and 0b010_011) == 0b010_010 && composer.skipping) {
    composer.skipToGroupEnd()
  } else {
    // This is inlined from currentRecomposeScope, since we can't call that composable property
    // from here.
    val recomposeScope = composer.recomposeScope!!
    renderContext.updateRecomposeScope(recomposeScope)
    composer.recordUsed(recomposeScope)

    newValue =
      renderContext.renderSelf(
        props = props,
        onOutput = onOutput,
        // Reuse the change information we already calculated so we don't have to call equals again.
        didPropsChange = (dirty and 0b010) != 0b010,
        didOnOutputChange = (dirty and 0b010_000) != 0b010_000,
        composer = composer,
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
      // Producer was skipped, or ran but returned the same instance, return from the cache.
      oldValue
    } else {
      // Producer ran, update the cache and return its new value.
      composer.updateRememberedValue(newValue)

      // When we're recomposed directly, we obviously can't return returnValue to the original
      // caller,
      // so just invalidate it instead. It will eventually recompose after we're done in the same
      // frame,
      // and when it does so it should hit the cache (unless the caller passes a new producer).
      if (invalidateCallerOnNewValue) {
        callerRecomposeScope.invalidate()
      }
      newValue
    }
  // endregion

  // This lambda allocation happens every time this workflow is re-rendered. I suspect this is
  // part of the reason why rerendering large swaths of the tree is inefficient, including first
  // render. But it's required for restartability. We could try making it a no-op everywhere except
  // the root node, since we're already invalidating all the way up the tree every time anyway.
  composer.endRestartGroup()?.updateScope { composer, changed ->
    // This lambda is called when producer is invalidated. The lambda must create a restartable
    // group with the same key to preserve positional identity.
    renderWorkflowRestartableImpl(
      props = props,
      onOutput = onOutput,
      renderContext = renderContext,
      invalidateCallerOnNewValue = true,
      callerRecomposeScope = callerRecomposeScope,
      composer = composer,
      // Set bits indicating that all parameters are known not to have changed. Note that we only
      // look at the first 2 sets, so we don't need to specify anything else.
      changed = changed or 0b010_010,
    )
  }
  return returnValue
}
