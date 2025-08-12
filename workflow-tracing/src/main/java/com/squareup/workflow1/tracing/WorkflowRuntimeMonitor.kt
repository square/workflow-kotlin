package com.squareup.workflow1.tracing

import androidx.collection.mutableLongObjectMapOf
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderPassSkipped
import com.squareup.workflow1.WorkflowInterceptor.RenderingConflated
import com.squareup.workflow1.WorkflowInterceptor.RenderingProduced
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.WorkflowInterceptor.RuntimeUpdate
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType.CASCADE
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType.RENDERING_CALLBACK
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType.WORKER_OUTPUT
import com.squareup.workflow1.tracing.RenderCause.Action
import com.squareup.workflow1.tracing.RenderCause.Callback
import com.squareup.workflow1.tracing.RenderCause.RootCreation
import com.squareup.workflow1.tracing.RenderCause.RootPropsChanged
import com.squareup.workflow1.tracing.RenderCause.WaitingForOutput
import com.squareup.workflow1.tracing.WorkflowRuntimeMonitor.ActionType.CascadeAction
import com.squareup.workflow1.tracing.WorkflowRuntimeMonitor.ActionType.QueuedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.nanoseconds

/**
 * This class has the following responsibilities:
 * 1. Collects records of each workflow action and each render pass with causes etc. (this is held
 *    by the [RuntimeTraceContext].)
 * 2. Forwards that information and the runtime update events to any [workflowRuntimeTracers].
 * 3. Tracks render passes via [renderPassTracker].
 * 4. Sends an update of all events that have occurred for each runtime loop to the [runtimeLoopListener].
 */
public class WorkflowRuntimeMonitor(
  override val runtimeName: String,
  private val workflowRuntimeTracers: List<WorkflowRuntimeTracer> = emptyList(),
  private val renderPassTracker: WorkflowRenderPassTracker? = null,
  private val runtimeLoopListener: WorkflowRuntimeLoopListener? = null,
) : WorkflowInterceptor, RuntimeTraceContext {

  private val chainedWorkflowRuntimeTracer: WorkflowRuntimeTracer = workflowRuntimeTracers
    .chained().apply {
      attachRuntimeContext(this@WorkflowRuntimeMonitor)
    }

  private var workerIncomingName: String? = null
  private val runtimeUpdates = RuntimeUpdates()

  private val rendering: Boolean
    get() = currentRenderCause != null

  private var lastRootProps: Any? = null
  private var currentActionHandlingChangedState = false

  // Cache workflow names by session id to save time.
  override val workflowSessionInfo = mutableLongObjectMapOf<WorkflowSessionInfo>()
  override val renderIncomingCauses: MutableList<RenderCause> = mutableListOf()
  override var previousRenderCause: RenderCause? = null
  override var currentRenderCause: RenderCause? = null
  override lateinit var configSnapshot: ConfigSnapshot

  override fun addRuntimeUpdate(event: RuntimeUpdateLogLine) {
    runtimeUpdates.logUpdate(event)
  }

  /**
   * Called the first time any Workflow is rendered - which starts its 'session'.
   * @see [WorkflowSession] for more information on the lifecycle of a session.
   */
  override fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    onWorkflowStarted(session)
    chainedWorkflowRuntimeTracer.onWorkflowSessionStarted(workflowScope, session)

    val workflowJob = workflowScope.coroutineContext[Job]!!
    workflowJob.invokeOnCompletion {
      onWorkflowStopped(session.sessionId)
      chainedWorkflowRuntimeTracer.onWorkflowSessionStopped(session.sessionId)
    }
  }

  /**
   * Helper method to populate tracking meta-data for a [WorkflowSession].
   *
   * Note that if `session.parent == null` (this is the root workflow), then this is actually called
   * before [onRenderAndSnapshot] as the root workflow's node is created.
   */
  private fun onWorkflowStarted(
    session: WorkflowSession
  ) {
    val sessionInfo = WorkflowSessionInfo(session)
    workflowSessionInfo[session.sessionId] = sessionInfo

    if (session.isRootWorkflow) {
      // Cache the config snapshot for this whole runtime.
      configSnapshot = ConfigSnapshot(session.runtimeConfig)
      check(renderIncomingCauses.isEmpty()) {
        "Workflow runtime for $runtimeName already has incoming render on creation triggered by " +
          "${renderIncomingCauses.lastOrNull()}"
      }
      renderIncomingCauses.add(
        RootCreation(
          runnerName = runtimeName,
          workflowName = sessionInfo.logName
        )
      )
    } else {
      check(rendering) {
        "Non root workflow ${sessionInfo.name} in $runtimeName first created outside of a" +
          " rendering. This should not be possible."
      }
    }
  }

  /**
   * Helper function called when the job backing the [WorkflowSession] ends.
   */
  private fun onWorkflowStopped(workflowSessionId: Long) {
    workflowSessionInfo -= workflowSessionId
  }

  /**
   * Forwards calls to [Workflow::initialState] to [workflowRuntimeTracers].
   */
  override fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope,
    proceed: (P, Snapshot?, CoroutineScope) -> S,
    session: WorkflowSession
  ): S {
    if (session.isRootWorkflow) {
      // For the root workflow, this is called before [onRenderAndSnapshot]. Setup the 'lastProps'
      // before that, so we don't think they've changed. We've set the `renderIncomingCause` as
      // creation in `onWorkflowStarted`.
      lastRootProps = props
    }
    return chainedWorkflowRuntimeTracer.onInitialState(
      props,
      snapshot,
      workflowScope,
      proceed,
      session
    )
  }

  /**
   * Forwards all calls to [Workflow::onPropsChanged] to the [workflowRuntimeTracers].
   */
  override fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S {
    return chainedWorkflowRuntimeTracer.onPropsChanged(old, new, state, proceed, session)
  }

  /**
   * Instruments a full 'render pass', which includes render for the whole tree, as well as
   * snapshotting the whole tree. We forward to [workflowRuntimeTracers], and also count it with the
   * [renderPassTracker].
   */
  override fun <P, R> onRenderAndSnapshot(
    renderProps: P,
    proceed: (P) -> RenderingAndSnapshot<R>,
    session: WorkflowSession
  ): RenderingAndSnapshot<R> {
    // Workflow deduplicates new props that are equal, so we don't need to do an equality check here
    // we know that !== is semantically equivalent to != (and faster)
    if (renderProps !== lastRootProps) {
      check(renderIncomingCauses.isEmpty()) {
        "$runtimeName onRenderAndSnapshot() triggered by changing props, should not already have " +
          "render incoming ${renderIncomingCauses.lastOrNull()}"
      }
      lastRootProps = renderProps
      renderIncomingCauses.add(RootPropsChanged)
      chainedWorkflowRuntimeTracer.onRootPropsChanged(session)
    } else {
      check(renderIncomingCauses.isNotEmpty()) {
        "$runtimeName onRenderAndSnapshot() even though renderIncomingCauses is empty. " +
          "previousRenderCause=$previousRenderCause, " +
          "rendering=${currentRenderCause != null}"
      }
    }

    check(!rendering) {
      "$runtimeName onRenderAndSnapshot() even though rendering already true. " +
        "renderIncomingCause=${renderIncomingCauses.lastOrNull()}"
    }
    // At this point renderIncomingCause can't be empty, we covered all cases.
    // The last render cause will be the most recent one.
    val localCurrentRenderCause = renderIncomingCauses.last()
    previousRenderCause = localCurrentRenderCause
    currentRenderCause = localCurrentRenderCause
    workerIncomingName = null

    val renderPassStartUptimeNanos = System.nanoTime()
    return chainedWorkflowRuntimeTracer
      .onRenderAndSnapshot(renderProps, proceed, session)
      .also {
        currentRenderCause = null
        runtimeUpdates.logUpdate(RenderLogLine)
        val renderPassDurationUptimeNanos = System.nanoTime() - renderPassStartUptimeNanos
        renderPassTracker?.recordRenderPass(
          RenderPassInfo(
            runnerName = runtimeName,
            renderCause = localCurrentRenderCause,
            durationUptime = renderPassDurationUptimeNanos.nanoseconds,
          )
        )
      }
  }

  /**
   * Instruments all calls to [Workflow::render], forwarding them to [workflowRuntimeTracers].
   */
  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    check(rendering) {
      "$runtimeName should be rendering"
    }

    val monitoringRenderContextInterceptor = MonitoringRenderContextInterceptor<P, S, O>(
      workflowName = requireNotNull(workflowSessionInfo[session.sessionId]) {
        "Expected session info for sessionId ${session.sessionId} but found none."
      }.logName
    )
    return chainedWorkflowRuntimeTracer.onRender(
      renderProps = renderProps,
      renderState = renderState,
      context = context,
      session = session,
      proceed = { p: P, s: S, rci: RenderContextInterceptor<P, S, O>? ->
        proceed(
          p,
          s,
          rci?.let { monitoringRenderContextInterceptor.wrap(rci) }
            ?: monitoringRenderContextInterceptor
        )
      }
    )
  }

  /**
   * Instruments all calls to [Workflow::snapshotState], forwarding them to [workflowRuntimeTracers].
   */
  override fun onSnapshotStateWithChildren(
    proceed: () -> TreeSnapshot,
    session: WorkflowSession
  ): TreeSnapshot {
    return chainedWorkflowRuntimeTracer.onSnapshotStateWithChildren(proceed, session)
  }

  /**
   * Updates the [runtimeLoopListener], instruments the render pass or skip.
   */
  override fun onRuntimeUpdate(update: RuntimeUpdate) {
    chainedWorkflowRuntimeTracer.onRuntimeUpdateEnhanced(
      update,
      currentActionHandlingChangedState,
      configSnapshot
    )
    when (update) {
      RenderPassSkipped -> {
        previousRenderCause = renderIncomingCauses.lastOrNull()
        runtimeUpdates.logUpdate(SkipLogLine)
      }

      RenderingConflated -> {
        // runtimeUpdates.logUpdate(ConflatedLogLine)
      }

      RenderingProduced -> {
        // runtimeUpdates.logUpdate(ProducedLogLine)
      }

      RuntimeSettled -> {
        runtimeLoopListener?.onRuntimeLoopSettled(
          configSnapshot,
          runtimeUpdates
        )
        currentActionHandlingChangedState = false
        renderIncomingCauses.clear()
      }
    }
  }

  /**
   * Wrapped [RenderContextInterceptor] that adds needed metadata tracking for children, side effects,
   * and actions.
   */
  private inner class MonitoringRenderContextInterceptor<P, S, O>(
    private val workflowName: String
  ) : RenderContextInterceptor<P, S, O> {

    /**
     * Instruments when an action is sent to the actionSink by a Workflow's handler (either
     * an event handler from the UI, or `renderChild`/`runningWorker` output handler).
     *
     * We wrap the action here with a [RuntimeMonitoringAction] so that we can monitor when it gets
     * applied.
     * Any action sent to the RenderContext's actionSink will become part of the global "queue" of
     * actions that the Workflow runtime is looping over to process. The runtime will resume the loop
     * when any of these actions can be processed, which then initiates a render pass to update the
     * rendering after the (presumed) state change. Because these actions are coming off the queue to
     * start an action cascade, we call these types of actions [QueuedAction].
     *
     * The render pass will not occur if it is skipped due to the state equality optimization.
     */
    override fun onActionSent(
      action: WorkflowAction<P, S, O>,
      proceed: (WorkflowAction<P, S, O>) -> Unit
    ) {
      proceed(
        RuntimeMonitoringAction(
          delegateAction = action,
          actionName = action.toLoggingShortName(),
          actionType = QueuedAction,
        )
      )
    }

    /**
     * This intercepts the corresponding output handler for the child to add wrapping
     * to the action that is produced from it. We wrap this action with [RuntimeMonitoringAction]
     * but we specify it as a [CascadeAction] as this is an action that is applied synchronously
     * as part of an action cascade that originated with a [QueuedAction].
     *
     * Note that for every [com.squareup.workflow1.Worker] a child workflow is created and rendered,
     * so this will be part of the callstack for that. In that case the output of the handler will
     * contain the worker action signature. We can detect that with
     * [Worker.WORKER_OUTPUT_ACTION_NAME].
     */
    override fun <CP, CO, CR> onRenderChild(
      child: Workflow<CP, CO, CR>,
      childProps: CP,
      key: String,
      handler: (CO) -> WorkflowAction<P, S, O>,
      proceed: (
        child: Workflow<CP, CO, CR>,
        childProps: CP,
        key: String,
        handler: (CO) -> WorkflowAction<P, S, O>
      ) -> CR
    ): CR {
      return proceed(child, childProps, key) { output ->
        val childOutputString = getWfLogString(output)
        val delegateAction = handler(output)
        val actionName = delegateAction.toLoggingShortName()
        RuntimeMonitoringAction(
          delegateAction = delegateAction,
          actionName = actionName,
          actionType = CascadeAction(
            childOutputString = childOutputString
          ),
        )
      }
    }

    /**
     * Class to instrument the application of actions. We simply wrap the [delegateAction] with
     * meta-data that helps us trace it. This class is used for both a [QueuedAction] that was sent to
     * the actionSink by an asynchronous event, and for a [CascadeAction] that is applied synchronously
     * as part of the action cascade. That is specified by [actionType].
     */
    private inner class RuntimeMonitoringAction<P, S, O>(
      private val delegateAction: WorkflowAction<P, S, O>,
      private val actionName: String,
      private val actionType: ActionType
    ) : WorkflowAction<P, S, O>() {
      // Use the delegate [debuggingName] so from the perspective of breadcrumbs this tracing wrapper
      // is invisible.
      override val debuggingName: String
        get() = delegateAction.debuggingName

      /**
       * Adds instrumentation to the application of an action (a state change).
       * This is also where we establish rendering causes from whatever [QueuedAction] was applied
       * to start the action cascade.
       */
      override fun Updater.apply() {
        // See https://github.com/square/workflow-kotlin/issues/391. We have to listen to the 2nd
        // action in the cascade to get a useful ref on which Worker's handler was firing. This is
        // because Workers use an underlying Workflow and an intermediate action, which is the
        // QueuedAction, in their implementation. So yes, we use an implementation detail here to
        // detect that. The issue still tracks upstreaming this into the library.
        val isWorkerQueuedAction = actionName.contains(Worker.WORKER_OUTPUT_ACTION_NAME)
        // This is non-null when we are applying the action from the Worker Output handler, in that
        // case it is equal to the name of the underlying Worker workflow, which is the type of the
        // worker.
        val workerLogName: String? = workerIncomingName
        if (actionType is QueuedAction) {
          val newRenderingCause: RenderCause = if (isWorkerQueuedAction) {
            workerIncomingName = workflowName
            WaitingForOutput(workflowName)
          } else {
            Callback(actionName, workflowName)
          }
          renderIncomingCauses.add(newRenderingCause)
        } else if (
          actionType is CascadeAction &&
          workerIncomingName != null
        ) {
          // WaitingForOutput should be the last cause added.
          val lastCause = renderIncomingCauses.removeLastOrNull()
          check(lastCause!! is WaitingForOutput) {
            "Expecting to receive action handling for worker output. Instead $lastCause."
          }
          // This is the real output handler action from the runningWorker call, thus the more
          // 'recognizable' cause of the render pass.
          renderIncomingCauses.add(Action(actionName, workerIncomingName, workflowName))
          workerIncomingName = null
        }
        val oldState = state
        // Apply the actual action!
        val (newState, actionApplied) = delegateAction.applyTo(props, state)
          .also { (newState, actionApplied) ->
            state = newState
            actionApplied.output?.let { setOutput(it.value) }
          }
        currentActionHandlingChangedState =
          // In a cascade from a child or applying multiple actions.
          currentActionHandlingChangedState ||
          actionApplied.stateChanged

        if (!isWorkerQueuedAction) {
          // Do not log for the first 'QueueAction' of the Worker (implementation detail that is
          // handled in the logging of the first CascadeAction).
          logActionApplied(
            workerCause = workerLogName,
            actionName = actionName,
            actionType = actionType,
            props = props,
            oldState = oldState,
            newState = newState,
            actionApplied = actionApplied
          )
        }
      }

      private fun logActionApplied(
        workerCause: String?,
        actionName: String,
        actionType: ActionType,
        props: P,
        oldState: S,
        newState: S,
        actionApplied: ActionApplied<O>,
      ) {
        val workflowLogType: WorkflowActionLogType = if (workerCause != null) {
          WORKER_OUTPUT
        } else if (actionType is QueuedAction) {
          // We don't call `logOutcome` for the initial Worker queued action. So all are callbacks.
          RENDERING_CALLBACK
        } else {
          CASCADE
        }
        val outputReceivedString: String? = if (actionType is CascadeAction) {
          actionType.childOutputString
        } else {
          null
        }
        val name = if (workerCause != null) {
          "R($workerCause)/W($workflowName)"
        } else {
          "W($workflowName)"
        }
        runtimeUpdates.logUpdate(
          ActionAppliedLogLine(
            type = workflowLogType,
            name = name,
            actionName = actionName,
            propsOrNull = props.takeIf { it !is Unit },
            oldState = oldState,
            newState = newState,
            outputOrNull = actionApplied.output,
            outputReceivedString = outputReceivedString,
          )
        )
      }
    }
  }

  public sealed interface ActionType {
    /**
     * A [QueuedAction] is an action that is queued at the event sink (from Worker Output or a
     * Rendering Callback.
     */
    public data object QueuedAction : ActionType

    /**
     * A [CascadeAction] is an action applied as part of the cascade of Output up the hierarchy.
     *
     * @param childOutputString contains debug info about the output received by this action.
     */
    public class CascadeAction(
      val childOutputString: String,
    ) : ActionType
  }
}
