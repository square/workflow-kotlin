package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
internal class WorkflowNode<PropsT, StateT, OutputT, RenderingT>(
  val id: WorkflowNodeId,
  workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  override val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  override val workflowTracer: WorkflowTracer? = null,
  private val emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult =
    { it },
  override val parent: WorkflowSession? = null,
  private val interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
) : CoroutineScope, SideEffectRunner, RememberStore, WorkflowSession {

  /**
   * Context that has a job that will live as long as this node.
   * Also adds a debug name to this coroutine based on its ID.
   */
  override val coroutineContext = baseContext + Job(baseContext[Job]) + CoroutineName(id.toString())

  // WorkflowInstance properties
  override val identifier: WorkflowIdentifier get() = id.identifier
  override val renderKey: String get() = id.name
  override val sessionId: Long = idCounter.createId()
  private var cachedWorkflowInstance: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
  private var interceptedWorkflowInstance: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>

  private val subtreeManager = SubtreeManager(
    snapshotCache = snapshot?.childTreeSnapshots,
    contextForChildren = coroutineContext,
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
   * The state of this node or that of one of our descendants changed.
   */
  private var subtreeStateDirty: Boolean = true

  /**
   * The state of this node changed.
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
    interceptor.onSessionStarted(this, this)

    cachedWorkflowInstance = workflow
    interceptedWorkflowInstance = interceptor.intercept(cachedWorkflowInstance, this)
    state = interceptedWorkflowInstance.initialState(initialProps, snapshot?.workflowSnapshot, this)
  }

  override fun toString(): String {
    val parentDescription = parent?.let { "WorkflowInstance(…)" }
    return "WorkflowInstance(" +
      "identifier=$identifier, " +
      "renderKey=$renderKey, " +
      "instanceId=$sessionId, " +
      "parent=$parentDescription" +
      ")"
  }

  /**
   * Walk the tree of workflows, rendering each one and using
   * [RenderContext][com.squareup.workflow1.BaseRenderContext] to give its children a chance to
   * render themselves and aggregate those child renderings.
   */
  @Suppress("UNCHECKED_CAST")
  fun render(
    workflow: StatefulWorkflow<PropsT, *, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT =
    renderWithStateType(workflow as StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>, input)

  /**
   * Walk the tree of state machines again, this time gathering snapshots and aggregating them
   * automatically.
   */
  fun snapshot(workflow: StatefulWorkflow<*, *, *, *>): TreeSnapshot {
    @Suppress("UNCHECKED_CAST")
    val typedWorkflow = workflow as StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
    maybeUpdateCachedWorkflowInstance(typedWorkflow)
    return interceptor.onSnapshotStateWithChildren({
      val childSnapshots = subtreeManager.createChildSnapshots()
      val rootSnapshot = interceptedWorkflowInstance.snapshotState(state)
      TreeSnapshot(
        workflowSnapshot = rootSnapshot,
        // Create the snapshots eagerly since subtreeManager is mutable.
        childTreeSnapshots = { childSnapshots }
      )
    }, this)
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

  /**
   * Gets the next [result][ActionProcessingResult] from the state machine. This will be an
   * [OutputT] or null.
   *
   * Walk the tree of state machines, asking each one to wait for its next event. If something happen
   * that results in an output, that output is returned. Null means something happened that requires
   * a re-render, e.g. my state changed or a child state changed.
   *
   * It is an error to call this method after calling [cancel].
   *
   * @return [Boolean] whether or not the queues were empty for this node and its children at the
   *    time of suspending.
   */
  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  fun onNextAction(
    selector: SelectBuilder<ActionProcessingResult>,
    skipChangedNodes: Boolean = false
  ): Boolean {
    val shouldListenForEvents = !skipChangedNodes || !selfStateDirty

    var empty = if (shouldListenForEvents) {
      // Listen for any child workflow events.
      subtreeManager.onNextChildAction(selector, skipChangedNodes)
    } else {
      // Our state changed and we are skipping changed nodes, so our actions are empty from
      // this node down.
      true
    }

    empty = empty && (eventActionsChannel.isEmpty || eventActionsChannel.isClosedForReceive)

    if (shouldListenForEvents) {
      // Listen for any events.
      with(selector) {
        eventActionsChannel.onReceive { action ->
          return@onReceive applyAction(action)
        }
      }
    }
    return empty
  }

  /**
   * Cancels this state machine host, and any coroutines started as children of it.
   *
   * This must be called when the caller will no longer call [onNextAction]. It is an error to call [onNextAction]
   * after calling this method.
   */
  fun cancel(cause: CancellationException? = null) {
    // No other cleanup work should be done in this function, since it will only be invoked when
    // this workflow is *directly* discarded by its parent (or the host).
    // If you need to do something whenever this workflow is torn down, add it to the
    // invokeOnCompletion handler for the Job above.
    coroutineContext.cancel(cause)
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
   *
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
    selfStateDirty = actionApplied.stateChanged
    // Our state changed or one of our children's state changed.
    subtreeStateDirty = aggregateActionApplied.stateChanged
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
      val scope = this + CoroutineName("sideEffect[$key] for $id")
      val job = scope.launch(start = LAZY, block = sideEffect)
      SideEffectNode(key, job)
    }
  }
}
