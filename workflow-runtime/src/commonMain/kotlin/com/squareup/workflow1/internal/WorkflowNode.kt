package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.intercept
import com.squareup.workflow1.internal.RealRenderContext.SideEffectRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

/**
 * A node in a state machine tree. Manages the actual state for a given [Workflow].
 *
 * @param emitOutputToParent A function that this node will call when it needs to emit an output
 * value to its parent. Returns either the output to be emitted from the root workflow, or null.
 * @param baseContext [CoroutineContext] that is appended to the end of the context used to launch
 * worker coroutines. This context will override anything from the workflow's scope and any other
 * hard-coded values added to worker contexts. It must not contain a [Job] element (it would violate
 * structured concurrency).
 */
internal class WorkflowNode<PropsT, StateT, OutputT, RenderingT>(
  val id: WorkflowNodeId,
  workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  private val emitOutputToParent: (OutputT) -> ActionProcessingResult? = { WorkflowOutput(it) },
  override val parent: WorkflowSession? = null,
  private val interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
) : CoroutineScope, SideEffectRunner, WorkflowSession {

  /**
   * Context that has a job that will live as long as this node.
   * Also adds a debug name to this coroutine based on its ID.
   */
  override val coroutineContext = baseContext + Job(baseContext[Job]) + CoroutineName(id.toString())

  // WorkflowInstance properties
  override val identifier: WorkflowIdentifier get() = id.identifier
  override val renderKey: String get() = id.name
  override val sessionId: Long = idCounter.createId()

  private val subtreeManager = SubtreeManager(
    snapshotCache = snapshot?.childTreeSnapshots,
    contextForChildren = coroutineContext,
    emitActionToParent = ::applyAction,
    workflowSession = this,
    interceptor = interceptor,
    idCounter = idCounter
  )
  private val sideEffects = ActiveStagingList<SideEffectNode>()
  private var lastProps: PropsT = initialProps
  private val eventActionsChannel =
    Channel<WorkflowAction<PropsT, StateT, OutputT>>(capacity = UNLIMITED)
  private var state: StateT

  private val baseRenderContext = RealRenderContext(
    renderer = subtreeManager,
    sideEffectRunner = this,
    eventActionsChannel = eventActionsChannel
  )
  private val context = RenderContext(baseRenderContext, workflow)

  init {
    interceptor.onSessionStarted(this, this)

    state = interceptor.intercept(workflow, this)
      .initialState(initialProps, snapshot?.workflowSnapshot)
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
    return interceptor.onSnapshotStateWithChildren({
      val childSnapshots = subtreeManager.createChildSnapshots()
      val rootSnapshot = interceptor.intercept(typedWorkflow, this)
        .snapshotState(state)
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
      require(key != it.key) { "Expected side effect keys to be unique: \"$key\"" }
    }

    sideEffects.retainOrCreate(
      predicate = { key == it.key },
      create = { createSideEffectNode(key, sideEffect) }
    )
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
  @OptIn(ExperimentalCoroutinesApi::class)
  fun onNextAction(selector: SelectBuilder<ActionProcessingResult?>): Boolean {
    // Listen for any child workflow updates.
    var empty = subtreeManager.onNextChildAction(selector)

    empty = empty && (eventActionsChannel.isEmpty || eventActionsChannel.isClosedForReceive)

    // Listen for any events.
    with(selector) {
      eventActionsChannel.onReceive { action ->
        return@onReceive applyAction(action)
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
   * Contains the actual logic for [render], after we've casted the passed-in [Workflow]'s
   * state type to our `StateT`.
   */
  private fun renderWithStateType(
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT {
    updatePropsAndState(workflow, props)

    baseRenderContext.unfreeze()
    val rendering = interceptor.intercept(workflow, this)
      .render(props, state, context)
    baseRenderContext.freeze()

    // Tear down workflows and workers that are obsolete.
    subtreeManager.commitRenderedChildren()
    // Side effect jobs are launched lazily, since they can send actions to the sink, and can only
    // be started after context is frozen.
    sideEffects.forEachStaging { it.job.start() }
    sideEffects.commitStaging { it.job.cancel() }

    return rendering
  }

  private fun updatePropsAndState(
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
    newProps: PropsT
  ) {
    if (newProps != lastProps) {
      val newState = interceptor.intercept(workflow, this)
        .onPropsChanged(lastProps, newProps, state)
      state = newState
    }
    lastProps = newProps
  }

  /**
   * Applies [action] to this workflow's [state] and
   * [emits an output to its parent][emitOutputToParent] if necessary.
   */
  private fun applyAction(
    action: WorkflowAction<PropsT, StateT, OutputT>
  ): ActionProcessingResult? {
    val (newState, outputOrNull) = action.applyTo(lastProps, state)
    state = newState
    return outputOrNull?.let { emitOutputToParent(it.value) }
  }

  private fun createSideEffectNode(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ): SideEffectNode {
    val scope = this + CoroutineName("sideEffect[$key] for $id")
    val job = scope.launch(start = LAZY, block = sideEffect)
    return SideEffectNode(key, job)
  }
}
