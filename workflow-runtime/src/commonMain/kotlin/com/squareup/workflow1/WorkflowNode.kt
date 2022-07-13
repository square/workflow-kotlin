package com.squareup.workflow1

import com.squareup.workflow1.RealRenderContext.SideEffectRunner
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.internal.SideEffectNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
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
public open class WorkflowNode<PropsT, StateT, OutputT, RenderingT>(
  public val id: WorkflowNodeId,
  protected val workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  private val initialProps: PropsT,
  private val initialSnapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  protected val emitOutputToParent: (OutputT) -> Any? = { WorkflowOutput(it) },
  override val parent: WorkflowSession? = null,
  protected val interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
) : CoroutineScope, SideEffectRunner, WorkflowSession {

  /**
   * Context that has a job that will live as long as this node.
   * Also adds a debug name to this coroutine based on its ID.
   */
  override val coroutineContext: CoroutineContext =
    baseContext + Job(baseContext[Job]) + CoroutineName(id.toString())

  // WorkflowInstance properties
  override val identifier: WorkflowIdentifier get() = id.identifier
  override val renderKey: String get() = id.name
  override val sessionId: Long = idCounter.createId()

  protected open val subtreeManager: SubtreeManager<PropsT, StateT, OutputT> by lazy {
    SubtreeManager(
      snapshotCache = initialSnapshot?.childTreeSnapshots,
      contextForChildren = coroutineContext,
      emitActionToParent = ::applyAction,
      workflowSession = this,
      interceptor = interceptor,
      idCounter = idCounter
    )
  }

  private val sideEffects: ActiveStagingList<SideEffectNode> = ActiveStagingList()
  protected var lastProps: PropsT = initialProps
  protected val eventActionsChannel: Channel<WorkflowAction<PropsT, StateT, OutputT>> =
    Channel(capacity = UNLIMITED)

  private var backingState: StateT? = null

  protected open var state: StateT
    get() {
      requireNotNull(backingState)
      return backingState!!
    }
    set(value) {
      backingState = value
    }

  /**
   * Initialize the session to handle polymorphic class creation.
   *
   * TODO: Handle this better as this is a very dangerous implicit API connection.
   */
  public fun startSession() {
    interceptor.onSessionStarted(workflowScope = this, session = this)
    state = interceptor.intercept(workflow = workflow, workflowSession = this)
      .initialState(initialProps, initialSnapshot?.workflowSnapshot)
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
  public fun render(
    workflow: StatefulWorkflow<PropsT, *, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT =
    renderWithStateType(workflow as StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>, input)

  /**
   * Walk the tree of state machines again, this time gathering snapshots and aggregating them
   * automatically.
   */
  public fun snapshot(workflow: StatefulWorkflow<*, *, *, *>): TreeSnapshot {
    // TODO: Figure out how to use `rememberSaveable` for Compose runtime here.
    @Suppress("UNCHECKED_CAST")
    val typedWorkflow = workflow as StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
    val childSnapshots = subtreeManager.createChildSnapshots()
    val rootSnapshot = interceptor.intercept(typedWorkflow, this)
      .snapshotState(state)
    return TreeSnapshot(
      workflowSnapshot = rootSnapshot,
      // Create the snapshots eagerly since subtreeManager is mutable.
      childTreeSnapshots = { childSnapshots }
    )
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
   * Gets the next [output][OutputT] from the state machine.
   *
   * Walk the tree of state machines, asking each one to wait for its next event. If something happen
   * that results in an output, that output is returned. Null means something happened that requires
   * a re-render, e.g. my state changed or a child state changed.
   *
   * It is an error to call this method after calling [cancel].
   */
  internal fun tick(selector: SelectBuilder<ActionProcessingResult?>) {
    // Listen for any child workflow updates.
    subtreeManager.tickChildren(selector)

    // Listen for any events.
    with(selector) {
      eventActionsChannel.onReceive { action ->
        return@onReceive applyAction(action)
      }
    }
  }

  /**
   * Cancels this state machine host, and any coroutines started as children of it.
   *
   * This must be called when the caller will no longer call [tick]. It is an error to call [tick]
   * after calling this method.
   */
  internal fun cancel(cause: CancellationException? = null) {
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

    val context = RealRenderContext(
      renderer = subtreeManager,
      sideEffectRunner = this,
      eventActionsChannel = eventActionsChannel
    )
    val rendering = interceptor.intercept(workflow, this)
      .render(props, state, RenderContext(context, workflow))
    context.freeze()

    commitAndUpdateScopes()

    return rendering
  }

  protected fun commitAndUpdateScopes() {
    // Tear down workflows and workers that are obsolete.
    subtreeManager.commitRenderedChildren()
    // Side effect jobs are launched lazily, since they can send actions to the sink, and can only
    // be started after context is frozen.
    sideEffects.forEachStaging { it.job.start() }
    sideEffects.commitStaging { it.job.cancel() }
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
  protected fun <T : Any> applyAction(action: WorkflowAction<PropsT, StateT, OutputT>): T? {
    val (newState, tickResult) = action.applyTo(lastProps, state)
    state = newState
    @Suppress("UNCHECKED_CAST")
    return tickResult?.let { emitOutputToParent(it.value) } as T?
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
