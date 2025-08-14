package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.NullableInitBox
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.intercept
import com.squareup.workflow1.internal.RealRenderContext.RememberStore
import com.squareup.workflow1.internal.RealRenderContext.SideEffectRunner
import com.squareup.workflow1.trace
import com.squareup.workflow1.workflowSessionToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KType

/**
 * A node in a state machine tree. Manages the actual state for a given [Workflow].
 *
 * @param emitAppliedActionToParent A function that this node will call to pass the result of
 * applying an action to its parent.
 * @param baseContext [CoroutineContext] that is appended to the end of the context used to launch
 * worker coroutines. This context will override anything from the workflow's scope and any other
 * hard-coded values added to worker contexts. It must not contain a [Job] element (it would violate
 * structured concurrency).
 */
@OptIn(WorkflowExperimentalApi::class, WorkflowExperimentalRuntime::class)
internal class StatefulWorkflowNode<PropsT, StateT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  override val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  override val workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  override val parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
) : WorkflowNode<PropsT, OutputT, RenderingT>(
  id = id,
  baseContext = baseContext,
  interceptor = interceptor,
  emitAppliedActionToParent = emitAppliedActionToParent,
),
  SideEffectRunner,
  RememberStore,
  WorkflowSession {

  // WorkflowSession properties
  override val identifier: WorkflowIdentifier get() = id.identifier
  override val renderKey: String get() = id.name
  override val sessionId: Long = idCounter.createId()
  private var cachedWorkflowInstance: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
  private var interceptedWorkflowInstance: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>

  override val session: WorkflowSession
    get() = this

  private val subtreeManager = SubtreeManager(
    snapshotCache = snapshot?.childTreeSnapshots,
    contextForChildren = scope.coroutineContext,
    emitActionToParent = ::applyAction,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    workflowSession = this,
    interceptor = interceptor,
    idCounter = idCounter
  )
  private val sideEffects = ActiveStagingList<SideEffectNode>()
  private val remembered = ActiveStagingList<RememberedNode<*>>()
  private var lastProps: PropsT = initialProps
  private var lastRendering: NullableInitBox<RenderingT> = NullableInitBox()
  private val eventActionsChannel =
    Channel<WorkflowAction<PropsT, StateT, OutputT>>(capacity = UNLIMITED)
  private var state: StateT

  /**
   * The state of this node or that of one of our descendants changed since we last rendered.
   */
  private var subtreeStateDirty: Boolean = true

  /**
   * The state of this node changed since we last rendered.
   */
  private var selfStateDirty: Boolean = true

  private val baseRenderContext = RealRenderContext(
    renderer = subtreeManager,
    sideEffectRunner = this,
    rememberStore = this,
    eventActionsChannel = eventActionsChannel,
    workflowTracer = workflowTracer,
    runtimeConfig = runtimeConfig
  )
  private val context = RenderContext(baseRenderContext, workflow)

  init {
    interceptor.onSessionStarted(workflowScope = scope, session = this)

    cachedWorkflowInstance = workflow
    interceptedWorkflowInstance = interceptor.intercept(
      workflow = cachedWorkflowInstance,
      workflowSession = this
    )
    state = interceptedWorkflowInstance.initialState(
      props = initialProps,
      snapshot = snapshot?.workflowSnapshot,
      workflowScope = scope
    )
  }

  override fun toString(): String = workflowSessionToString()

  /**
   * Walk the tree of workflows, rendering each one and using
   * [RenderContext][com.squareup.workflow1.BaseRenderContext] to give its children a chance to
   * render themselves and aggregate those child renderings.
   */
  @Suppress("UNCHECKED_CAST")
  override fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT = renderWithStateType(
    workflow = workflow.asStatefulWorkflow() as
      StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
    props = input
  )

  /**
   * Walk the tree of state machines again, this time gathering snapshots and aggregating them
   * automatically.
   */
  override fun snapshot(): TreeSnapshot {
    return interceptor.onSnapshotStateWithChildren(
      proceed = {
        val childSnapshots = subtreeManager.createChildSnapshots()
        val rootSnapshot = interceptedWorkflowInstance.snapshotState(state)
        TreeSnapshot(
          workflowSnapshot = rootSnapshot,
          // Create the snapshots eagerly since subtreeManager is mutable.
          childTreeSnapshots = { childSnapshots }
        )
      },
      session = this
    )
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    // Prevent duplicate side effects with the same key.
    sideEffects.forEachStaging {
      requireWithKey(key != it.key, key) { "Expected side effect keys to be unique: \"$key\"" }
    }

    sideEffects.retainOrCreate(
      predicate = { key == it.key },
      create = { createSideEffectNode(key, sideEffect) }
    )
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT {
    remembered.forEachStaging {
      requireWithKey(
        key != it.key || resultType != it.resultType || !inputs.contentEquals(it.inputs),
        stackTraceKey = key
      ) {
        "Expected unique combination of key, input types and result type: \"$key\""
      }
    }

    val result = remembered.retainOrCreate(
      predicate = {
        key == it.key && it.resultType == resultType && inputs.contentEquals(it.inputs)
      },
      create = { RememberedNode(key, resultType, inputs, calculation()) }
    )

    @Suppress("UNCHECKED_CAST")
    return result.lastCalculated as ResultT
  }

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    // Listen for any child workflow updates.
    subtreeManager.registerChildActionSelectors(selector)

    // Listen for any events.
    with(selector) {
      eventActionsChannel.onReceive { action ->
        return@onReceive applyAction(action)
      }
    }
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult {
    if (skipDirtyNodes && selfStateDirty) return ActionsExhausted

    val result = subtreeManager.applyNextAvailableChildAction(skipDirtyNodes)

    if (result == ActionsExhausted) {
      return eventActionsChannel.tryReceive().getOrNull()?.let { action ->
        applyAction(action)
      } ?: ActionsExhausted
    }
    return result
  }

  /**
   * Cancels this state machine host, and any coroutines started as children of it.
   *
   * This must be called when the caller will no longer call [registerTreeActionSelectors]. It is an
   * error to call [registerTreeActionSelectors] after calling this method.
   */
  override fun cancel(cause: CancellationException?) {
    super.cancel(cause)
    lastRendering = NullableInitBox()
  }

  /**
   * Call this after we have been passed any workflow instance, in [render] or [snapshot]. It may
   * have changed and we should check to see if we need to update our cached instances.
   */
  private fun maybeUpdateCachedWorkflowInstance(
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
  ) {
    if (workflow !== cachedWorkflowInstance) {
      // The instance has changed.
      cachedWorkflowInstance = workflow
      interceptedWorkflowInstance = interceptor.intercept(cachedWorkflowInstance, this)
    }
  }

  /**
   * Contains the actual logic for [render], after we've casted the passed-in [Workflow]'s
   * state type to our `StateT`.
   */
  private fun renderWithStateType(
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT {
    updatePropsAndState(props, workflow)

    if (!runtimeConfig.contains(PARTIAL_TREE_RENDERING) ||
      !lastRendering.isInitialized ||
      subtreeStateDirty
    ) {
      // If we haven't already updated the cached instance, better do it now!
      maybeUpdateCachedWorkflowInstance(workflow)

      baseRenderContext.unfreeze()
      lastRendering = NullableInitBox(interceptedWorkflowInstance.render(props, state, context))
      baseRenderContext.freeze()

      workflowTracer.trace("UpdateRuntimeTree") {
        // Tear down workflows and workers that are obsolete.
        subtreeManager.commitRenderedChildren()
        // Side effect jobs are launched lazily, since they can send actions to the sink, and can only
        // be started after context is frozen.
        sideEffects.forEachStaging { it.job.start() }
        sideEffects.commitStaging { it.job.cancel() }
        remembered.commitStaging { /* Nothing to clean up. */ }
      }
      // After we have rendered this subtree, we need another action in order for us to be
      // considered dirty again.
      subtreeStateDirty = false
      selfStateDirty = false
    }

    return lastRendering.getOrThrow()
  }

  /**
   * Update props if they have changed. If that happens, then check to see if we need
   * to update the cached workflow instance, then call [StatefulWorkflow.onPropsChanged] and
   * update the state from that. We consider any change to props as dirty because
   * the props themselves are used in [StatefulWorkflow.render] (they are the 'external' part of
   * the state) so we must re-render.
   */
  private fun updatePropsAndState(
    newProps: PropsT,
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  ) {
    if (newProps != lastProps) {
      maybeUpdateCachedWorkflowInstance(workflow)
      val newState = interceptedWorkflowInstance.onPropsChanged(lastProps, newProps, state)
      state = newState
      subtreeStateDirty = true
      selfStateDirty = true
    }
    lastProps = newProps
  }

  /**
   * Applies [action] to this workflow's [state] and then passes the resulting [ActionApplied]
   * via [emitAppliedActionToParent] to the parent, with additional information as to whether or
   * not this action has changed the current node's state.
   */
  private fun applyAction(
    action: WorkflowAction<PropsT, StateT, OutputT>,
    childResult: ActionApplied<*>? = null
  ): ActionProcessingResult {
    val (newState: StateT, actionApplied: ActionApplied<OutputT>) = action.applyTo(lastProps, state)
    state = newState
    // Aggregate the action with the child result, if any.
    val aggregateActionApplied = actionApplied.copy(
      // Changing state is sticky, we pass it up if it ever changed.
      stateChanged = actionApplied.stateChanged || (childResult?.stateChanged ?: false)
    )
    // Our state changed.
    selfStateDirty = selfStateDirty || actionApplied.stateChanged
    // Our state changed or one of our children's state changed.
    subtreeStateDirty = subtreeStateDirty || aggregateActionApplied.stateChanged
    return if (actionApplied.output != null ||
      runtimeConfig.contains(PARTIAL_TREE_RENDERING)
    ) {
      // If we are using the optimization, always return to the parent, so we carry a path that
      // notes that the subtree did change all the way to the root.
      //
      // We don't need that without the optimization because there is nothing
      // to output from the root of the runtime -- the output has propagated
      // as far as it needs to causing all corresponding state changes.
      //
      // However, the root and the path down to the changed nodes must always
      // re-render now, so this is the implementation detail of how we get
      // subtreeStateDirty = true on that entire path to the root.
      emitAppliedActionToParent(aggregateActionApplied)
    } else {
      aggregateActionApplied
    }
  }

  private fun createSideEffectNode(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ): SideEffectNode {
    return workflowTracer.trace("CreateSideEffectNode") {
      val scope = scope + CoroutineName("sideEffect[$key] for $id")
      val job = scope.launch(start = LAZY, block = sideEffect)
      SideEffectNode(key, job)
    }
  }
}
