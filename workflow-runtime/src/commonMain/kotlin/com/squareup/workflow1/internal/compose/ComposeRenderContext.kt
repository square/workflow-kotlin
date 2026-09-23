@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.toString
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
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KType
import kotlinx.coroutines.CoroutineScope

/**
 * The implementation of the [BaseRenderContext] that the workflow's `render` method sees when
 * running under the compose runtime.
 *
 * This class also holds most of the state and does most of the lifecycle management for the
 * workflow session.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
@Stable
internal class ComposeRenderContext<P, O, R>
private constructor(
  private val workflow: Workflow<P, O, R>,
  snapshot: Snapshot?,
  initialProps: P,
  private val workflowScope: CoroutineScope,
  private val parentRecomposeScope: RecomposeScope,
  private val config: WorkflowComposableRuntimeConfig,
  override val parent: WorkflowSession?,
  override val renderKey: String,
  private val saveableStateRegistry: SaveableStateRegistry?,
  private val saveableStateKey: String,
) :
  BaseRenderContext<P, Any?, O>,
  Sink<WorkflowAction<P, Any?, O>>,
  WorkflowSession,
  RecomposeScope,
  RememberObserver {

  constructor(
    saveableStateRegistry: SaveableStateRegistry?,
    saveableStateKey: String,
    workflow: Workflow<P, O, R>,
    initialProps: P,
    workflowScope: CoroutineScope,
    parentRecomposeScope: RecomposeScope,
    config: WorkflowComposableRuntimeConfig,
    parent: WorkflowSession?,
    renderKey: String,
  ) : this(
    snapshot =
      saveableStateRegistry?.let { restoreFromRegistry(saveableStateRegistry, saveableStateKey) },
    saveableStateRegistry = saveableStateRegistry,
    saveableStateKey = saveableStateKey,
    workflow = workflow,
    initialProps = initialProps,
    workflowScope = workflowScope,
    parentRecomposeScope = parentRecomposeScope,
    config = config,
    parent = parent,
    renderKey = renderKey,
  )

  private var recomposeScope: RecomposeScope? = null
  private var stateRegistryEntry: SaveableStateRegistry.Entry? = null

  private val interceptedWorkflow: StatefulWorkflow<P, Any?, O, R>
  private val applyActionLock = Lock()

  /** Helper for calling into Compose code from non-compose code. */
  private var trapdoor: Trapdoor? by threadLocalOf { null }

  /**
   * Effectively a `MutableState` that holds not only the actual workflow state, but also the last-
   * seen `props` and `onOutput` callbacks. See the docs on [WorkflowSnapshotState] for more info
   * about why we're using a custom state object.
   */
  private val state: WorkflowSnapshotState

  override val actionSink: Sink<WorkflowAction<P, Any?, O>>
    get() = this

  // region WorkflowSession implementation
  override val identifier: WorkflowIdentifier
    get() = workflow.identifier

  override val sessionId: Long = config.idCounter.createId()
  override val runtimeConfig: RuntimeConfig
    get() = config.runtimeConfig

  override val runtimeContext: CoroutineContext
    get() = workflowScope.coroutineContext

  override val workflowTracer: WorkflowTracer?
    get() = config.workflowTracer

  override fun toString(): String = workflowSessionToString()

  // endregion

  init {
    @Suppress("UNCHECKED_CAST")
    val statefulWorkflow = workflow.asStatefulWorkflow() as StatefulWorkflow<P, Any?, O, R>
    val interceptor = config.workflowInterceptor

    interceptor?.onSessionStarted(workflowScope = workflowScope, session = this)

    interceptedWorkflow =
      interceptor?.intercept(workflow = statefulWorkflow, workflowSession = this)
        ?: statefulWorkflow

    val initialState =
      config.workflowTracer.trace(InitialState) {
        interceptedWorkflow.initialState(
          props = initialProps,
          snapshot = snapshot,
          workflowScope = workflowScope,
        )
      }
    state = WorkflowSnapshotState(props = initialProps, onOutput = null, state = initialState)
  }

  override fun onRemembered() {
    if (saveableStateRegistry != null) {
      // Saving and restoring from the registry manually, instead of using rememberSaveable, saves
      // a lot on allocations and is faster too (see
      // https://github.com/square/workflow-kotlin/pull/1572).
      check(stateRegistryEntry == null)
      stateRegistryEntry =
        saveableStateRegistry.registerProvider(saveableStateKey) {
          interceptedWorkflow.snapshotState(state.peekState())
        }
    }
  }

  override fun onForgotten() {
    if (saveableStateRegistry != null) {
      stateRegistryEntry?.unregister()
      stateRegistryEntry = null
    }

    onDisposed()
  }

  override fun onAbandoned() {
    onDisposed()
  }

  @Suppress("UNCHECKED_CAST")
  fun renderSelf(
    props: Any?,
    onOutput: ((Any?) -> Unit)?,
    didPropsChange: Boolean?,
    didOnOutputChange: Boolean?,
    composer: Composer,
  ): R {
    trapdoor = Trapdoor(composer)
    val currentState =
      state.updateAndGetState(
        props,
        onOutput,
        didPropsChange = didPropsChange,
        didOnOutputChange = didOnOutputChange,
      ) { oldProps, oldState ->
        workflowTracer.traceNoFinally(OnPropsChanged) {
          interceptedWorkflow.onPropsChanged(
            old = oldProps as P,
            new = props as P,
            state = oldState,
          )
        }
      }
    return interceptedWorkflow
      .render(
        renderProps = props as P,
        renderState = currentState,
        context = RenderContext(this, interceptedWorkflow),
      )
      .also { trapdoor = null }
  }

  override fun send(value: WorkflowAction<P, Any?, O>) {
    workflowTracer.trace(SendAction) { applyAction(value) }
  }

  override fun runningSideEffect(key: String, sideEffect: suspend CoroutineScope.() -> Unit) {
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
    calculation: () -> ResultT,
  ): ResultT =
    requireTrapdoor("remember").inMovableGroup(GROUP_KEY, key) {
      // RememberStore (the traditional runtime's implementation of this call) keys the lifetime of
      // the memoization off the result type and inputs in addition to the explicit key. The data
      // key is only used to keep the memoized state consistent as the order and number of calls to
      // this context change. The resultType and inputs only need to reset the state for this
      // particular remember call, so they don't need to be part of the data key. We use a separate
      // key call since it's simpler than correctly folding multiple objects into a single data key
      // (which requires calling Composer.joinKey).
      key(resultType, *inputs) { remember(*inputs, calculation = calculation) }
    }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<P, Any?, O>,
  ): ChildRenderingT =
    requireTrapdoor("renderChild")
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
          renderKey = key,
          recomposeScope = this,
        )
      }

  /**
   * Updates the innermost recompose scope for this context. This must be called with the recompose
   * scope from inside the workflow's restartable group or [invalidate] won't actually trigger a
   * re-render.
   */
  fun updateRecomposeScope(recomposeScope: RecomposeScope) {
    this.recomposeScope = recomposeScope
  }

  override fun invalidate() {
    // When state changes, invalidate recompose scopes all the way up the tree in one shot because
    // trampolining compose scopes requires an extra frame per scope.
    parentRecomposeScope.invalidate()
    recomposeScope!!.invalidate()
  }

  private fun requireTrapdoor(operationName: String): Trapdoor =
    trapdoor ?: error("Cannot perform $operationName on RenderContext outside of render pass.")

  private fun onDisposed() {
    config.workflowInterceptor?.onSessionCancelled<P, Any?, O>(
      cause = null,
      // Compose workflow node actions are applied immediately so there can never be actions pending
      // when a workflow is disposed.
      droppedActions = emptyList(),
      session = this,
    )
  }

  private fun applyAction(action: WorkflowAction<P, Any?, O>) {
    check(trapdoor == null) { "Cannot send to action sink from render pass." }
    // Send can be called from any thread so wrap non-atomic reads/writes in a critical section.
    applyActionLock.withLock {
      @Suppress("UNCHECKED_CAST")
      state.applyAction(action as WorkflowAction<Any?, Any?, Any?>, onNewState = this::invalidate)
    }
  }

  companion object {
    /**
     * Hard-coded group key used for all groups that are created by [ComposeRenderContext]s. All
     * _types_ of children (child workflows, side effects, etc) must use the same group key so that
     * they can be moved among each other correctly.
     *
     * In most Compose code, this key would be generated by the compiler as a hash of the source
     * location of the function.
     */
    private const val GROUP_KEY = -9287345

    @Composable
    fun <P, O, R> rememberComposeRenderContext(
      workflow: Workflow<P, O, R>,
      initialProps: P,
      config: WorkflowComposableRuntimeConfig,
      parentSession: WorkflowSession?,
      renderKey: String,
      callerRecomposeScope: RecomposeScope,
    ): ComposeRenderContext<P, O, R> {
      val workflowScope = rememberCoroutineScope()
      val saveableStateRegistry = LocalSaveableStateRegistry.current
      val saveableStateKey = currentCompositeKeyHashCode
      return remember(saveableStateRegistry) {
        // 36 taken from compose sources.
        val stringKey = saveableStateKey.toString(radix = 36)
        ComposeRenderContext(
          initialProps = initialProps,
          workflow = workflow,
          parentRecomposeScope = callerRecomposeScope,
          workflowScope = workflowScope,
          config = config,
          parent = parentSession,
          renderKey = renderKey,
          saveableStateRegistry = saveableStateRegistry,
          saveableStateKey = stringKey,
        )
      }
    }
  }
}

private fun restoreFromRegistry(
  saveableStateRegistry: SaveableStateRegistry,
  key: String,
): Snapshot? {
  val restored = saveableStateRegistry.consumeRestored(key) as Snapshot?
  return restored
}
