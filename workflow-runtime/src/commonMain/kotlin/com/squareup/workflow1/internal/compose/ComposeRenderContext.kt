@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.ExplicitGroupsComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.identifier
import com.squareup.workflow1.intercept
import com.squareup.workflow1.internal.Lock
import com.squareup.workflow1.internal.compose.TraceLabels.InitialState
import com.squareup.workflow1.internal.compose.TraceLabels.OnPropsChanged
import com.squareup.workflow1.internal.compose.TraceLabels.SendAction
import com.squareup.workflow1.internal.createId
import com.squareup.workflow1.internal.getValue
import com.squareup.workflow1.internal.setValue
import com.squareup.workflow1.internal.threadLocalOf
import com.squareup.workflow1.internal.withLock
import com.squareup.workflow1.trace
import com.squareup.workflow1.traceNoFinally
import com.squareup.workflow1.workflowSessionToString
import kotlinx.coroutines.CoroutineScope
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KType

// TODO tests for all these methods keeping state when order changes. Make sure to exercise all keys
//  passed as data keys keep state, and all keys _not_ passed as data keys reset state when changed
//  but aren't interchangable relative to data keys.
@OptIn(ExperimentalComposeRuntimeApi::class)
@Stable
internal class ComposeRenderContext<P, O, R> private constructor(
  private val workflow: Workflow<P, O, R>,
  snapshot: Snapshot?,
  initialProps: P,
  private val workflowScope: CoroutineScope,
  private val recomposeScope: RecomposeScope,
  private val config: WorkflowComposableRuntimeConfig,
  override val parent: WorkflowSession?,
  override val renderKey: String,
) : BaseRenderContext<P, Any?, O>,
  Sink<WorkflowAction<P, Any?, O>>,
  WorkflowSession {

  private val interceptedWorkflow: StatefulWorkflow<P, Any?, O, R>
  private val state: PeekableMutableState<Any?>
  private val applyActionLock = Lock()
  private var trapdoor: Trapdoor? by threadLocalOf { null }

  // Render params from last render pass that are only used by actions, and don't need to be states.
  @Volatile private var onOutput: ((O) -> Unit)? = null
  @Volatile private var lastProps = initialProps

  override val actionSink: Sink<WorkflowAction<P, Any?, O>> get() = this

  // region WorkflowSession implementation
  override val identifier: WorkflowIdentifier get() = workflow.identifier
  override val sessionId: Long = config.idCounter.createId()
  override val runtimeConfig: RuntimeConfig get() = config.runtimeConfig
  override val runtimeContext: CoroutineContext get() = workflowScope.coroutineContext
  override val workflowTracer: WorkflowTracer? get() = config.workflowTracer
  override fun toString(): String = workflowSessionToString()
  // endregion

  init {
    @Suppress("UNCHECKED_CAST")
    val statefulWorkflow = workflow.asStatefulWorkflow() as StatefulWorkflow<P, Any?, O, R>
    val interceptor = config.workflowInterceptor

    interceptor?.onSessionStarted(
      workflowScope = workflowScope,
      session = this,
    )

    interceptedWorkflow =
      interceptor?.intercept(workflow = statefulWorkflow, workflowSession = this)
        ?: statefulWorkflow

    val initialState = config.workflowTracer.trace(InitialState) {
      interceptedWorkflow.initialState(
        props = initialProps,
        snapshot = snapshot,
        workflowScope = workflowScope,
      )
    }
    state = PeekableMutableState(initialState)
  }

  @Composable
  fun renderSelf(props: P): R = withTrapdoor {
    interceptedWorkflow.render(
      renderProps = props,
      renderState = state.value,
      context = RenderContext(this, interceptedWorkflow)
    )
  }

  override fun send(value: WorkflowAction<P, Any?, O>) {
    workflowTracer.trace(SendAction) {
      applyAction(value)
    }
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    // We pass the key as the movable data key instead of just passing it to LaunchedEffect since
    // we want this group to be movable, not just restartable.
    requireTrapdoor("runningSideEffect").inMovableGroup(GROUP_KEY, key) {
      // Can't use sideEffect as key since it is allocated anew every render pass, so it will always
      // be different.
      LaunchedEffect(Unit, block = sideEffect)
    }
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT = requireTrapdoor("remember").inMovableGroup(GROUP_KEY, key) {
    // RememberStore keys the lifetime of the memoization off the result type and inputs in addition
    // to the explicit key. The data key is only used to keep the memoized state consistent as the
    // order and number of calls to this context change. The resultType and inputs only need to
    // reset the state for this particular remember call, so they don't need to be part of the data
    // key. We use a separate key call since it's simpler than correctly folding multiple objects
    // into a single data key (which requires calling Composer.joinKey).
    key(resultType, *inputs) {
      remember(*inputs, calculation = calculation)
    }
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<P, Any?, O>
  ): ChildRenderingT = requireTrapdoor("renderChild")
    // child.identifier is usually cached by Workflow so calling it on recomposition is cheap.
    .inMovableGroup(GROUP_KEY, child.identifier, key) {
      // Memoize the onOutput function so that renderChild can skip whenever possible.
      val updatedHandler by rememberUpdatedState(handler)
      val childOnOutput: (ChildOutputT) -> Unit = remember {
        { output ->
          val action = updatedHandler(output)
          applyAction(action)
        }
      }

      renderWorkflow(
        workflow = child,
        props = props,
        onOutput = childOnOutput,
        config = config,
        parentSession = this,
        renderKey = key
      )
    }

  private fun requireTrapdoor(operationName: String): Trapdoor =
    trapdoor ?: error("Cannot perform $operationName on RenderContext outside of render pass.")

  // Since we need to manually deal with props diffing ourselves, tell compose not to generate its
  // own checking code which would result in redundant .equals() calls.
  @Suppress("ComposableNaming")
  @NonRestartableComposable
  @Composable
  private fun updatePropsAndOutput(
    props: P,
    onOutput: ((O) -> Unit)?,
  ) {
    // Use the slot table to detect props changes.
    Trapdoor.runIfValueChanged(props) { oldProps ->
      workflowTracer.traceNoFinally(OnPropsChanged) {
        @Suppress("UNCHECKED_CAST")
        state.value = interceptedWorkflow.onPropsChanged(
          old = oldProps,
          new = props,
          state = state.value
        )
      }
    }
    SideEffect {
      this.lastProps = props
      this.onOutput = onOutput
    }
  }

  private fun onDisposed() {
    config.workflowInterceptor?.onSessionCancelled<P, Any?, O>(
      cause = null,
      // Compose workflow node actions are applied immediately so there can never be actions pending
      // when a workflow is disposed.
      droppedActions = emptyList(),
      session = this
    )
  }

  private fun applyAction(action: WorkflowAction<P, Any?, O>) {
    check(trapdoor == null) { "Cannot send to action sink from render pass." }
    // Send can be called from any thread so wrap non-atomic reads/writes in a critical section.
    applyActionLock.withLock {
      val oldState = state.value
      val (newState, applied) = action.applyTo(lastProps, oldState)
      state.setWithInvalidator(newState, invalidator = { recomposeScope.invalidate() })

      // Propagate the output up the workflow tree. Propagation doesn't touch our state but still
      // should be part of the critical section: For an intermediate node, while it's propagating
      // an action from one part of its subtree, if another action comes in from a different part
      // of the subtree, the second action won't propagate until the first one is done. If we
      // moved this out of the lock, then the propagations of two actions from two subtrees could
      // be interleaved to their common ancestors.
      onOutput?.let { onOutput ->
        applied.output?.value?.let(onOutput)
      }
    }
  }

  private fun snapshot(): Snapshot? = interceptedWorkflow.snapshotState(state.value)

  // No need for the overhead of groups, this is only used in a very specific way when we're already
  // thinking about groups.
  @ExplicitGroupsComposable
  @Composable
  private inline fun <R> withTrapdoor(block: () -> R): R {
    Trapdoor.open { trapdoor ->
      this.trapdoor = trapdoor
      val result = block()
      this.trapdoor = null
      return result
    }
  }

  companion object {
    /**
     * Hard-coded group key used for all groups that are created by [ComposeRenderContext]s.
     * All _types_ of children (child workflows, side effects, etc) must use the same group key so
     * that they can be moved among each other correctly.
     *
     * In most Compose code, this key would be generated by the compiler as a hash of the source
     * location of the function.
     */
    private const val GROUP_KEY = -9287345

    @Composable
    fun <P, O, R> rememberComposeRenderContext(
      workflow: Workflow<P, O, R>,
      props: P,
      onOutput: ((O) -> Unit)?,
      config: WorkflowComposableRuntimeConfig,
      parentSession: WorkflowSession?,
      renderKey: String,
    ): ComposeRenderContext<P, O, R> {
      val workflowScope = rememberCoroutineScope()
      // The only reason we put the scope in a state is because if we just capture it directly
      // inside the rememberSaveable lambda, for some reason compose starts freaking out, groups get
      // removed for no reason and the RecomposeScope becomes invalid.
      // TODO find a less gross way to do this.
      val recomposeScope by rememberUpdatedState(currentRecomposeScope)
      val renderContext: ComposeRenderContext<P, O, R> = rememberSaveable(
        saver = Saver(
          initialProps = props,
          workflow = workflow,
          workflowScope = workflowScope,
          recomposeScope = recomposeScope,
          config = config,
          parentSession = parentSession,
          renderKey = renderKey,
        )
      ) {
        ComposeRenderContext(
          initialProps = props,
          workflow = workflow,
          recomposeScope = recomposeScope,
          workflowScope = workflowScope,
          config = config,
          parent = parentSession,
          renderKey = renderKey,
          snapshot = null,
        )
      }
      renderContext.updatePropsAndOutput(props, onOutput)

      // Values remembered by rememberSaveable don't get RememberObserver callbacks so we need to
      // use an effect for it.
      DisposableEffect(Unit) {
        onDispose {
          renderContext.onDisposed()
        }
      }

      return renderContext
    }
  }

  private class Saver<P, O, R>(
    private val initialProps: P,
    private val workflow: Workflow<P, O, R>,
    private val workflowScope: CoroutineScope,
    private val recomposeScope: RecomposeScope,
    private val config: WorkflowComposableRuntimeConfig,
    private val parentSession: WorkflowSession?,
    private val renderKey: String,
  ) : androidx.compose.runtime.saveable.Saver<ComposeRenderContext<P, O, R>, Snapshot> {
    override fun restore(value: Snapshot): ComposeRenderContext<P, O, R> =
      ComposeRenderContext(
        workflow = workflow,
        initialProps = initialProps,
        workflowScope = workflowScope,
        snapshot = value,
        recomposeScope = recomposeScope,
        config = config,
        parent = parentSession,
        renderKey = renderKey,
      )

    override fun SaverScope.save(value: ComposeRenderContext<P, O, R>): Snapshot? =
      value.snapshot()
  }
}
