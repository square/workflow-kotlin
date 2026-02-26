package com.squareup.workflow1.tracing

import androidx.collection.mutableLongObjectMapOf
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderPassSkipped
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.WorkflowInterceptor.RuntimeUpdate
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.tracing.ConfigSnapshot
// TraceInterface is in same package now
import com.squareup.workflow1.tracing.WorkflowRuntimeMonitor.ActionType
import com.squareup.workflow1.tracing.WorkflowRuntimeMonitor.ActionType.CascadeAction
import com.squareup.workflow1.tracing.WorkflowRuntimeMonitor.ActionType.QueuedAction
import com.squareup.workflow1.tracing.WorkflowRuntimeTracer
import com.squareup.workflow1.tracing.getWfLogString
import com.squareup.workflow1.tracing.toLoggingShortName
import com.squareup.workflow1.tracing.workerKey
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KType

/**
 * [WorkflowRuntimeTracer] plugin to add [TraceInterface] traces.
 * By default this uses [WorkflowTrace] which will use [androidx.tracing.Trace] calls that
 * will be received by the system and included in Perfetto traces.
 *
 * @param safeTrace The [TraceInterface] implementation to use for tracing.
 */
open class WorkflowTracer(
  private val safeTrace: TraceInterface = WorkflowTrace(isTraceable = false)
) : WorkflowRuntimeTracer() {

  private data class NameAndCookie(
    val name: String,
    val cookie: Int
  )

  private class SystemTraceState {
    var renderPassCount = 0

    // Some render passes are skipped if they have no state change. Count all triggers separately.
    var renderPassTriggerCount = 0
    val workflowAsyncSections = mutableLongObjectMapOf<NameAndCookie>()
    val workflowShortNamesById = mutableLongObjectMapOf<String>()
  }

  private val systemTraceState = if (safeTrace.isTraceable) {
    SystemTraceState()
  } else {
    null
  }

  private val isSystemTraceable: Boolean
    get() = systemTraceState != null

  private val isCurrentlySystemTracing: Boolean
    get() = systemTraceState != null && safeTrace.isCurrentlyTracing

  /**
   * If the build is traceable but we're not currently tracing, reset so that we start at 0 in
   * new traces.
   */
  private fun SystemTraceState.resetTraceCountsIfNotTracing() {
    if (!safeTrace.isCurrentlyTracing) {
      renderPassCount = 0
      renderPassTriggerCount = 0
      actionIndex = 0
      effectIndex = 0
    }
  }

  private fun renderPassNumber(): String {
    return ":${systemTraceState?.renderPassTriggerCount ?: 0}:"
  }

  /**
   * Log a Perfetto trace section that is simply meta-data that we use in post-processing.
   *
   * Format for labels in these sections:
   * - "W:<name>" is for a Workflow
   * - "R:<name>" is for a Worker
   * - "A:<name>" is for an Action
   */
  private fun infoSection(info: String) {
    safeTrace.logSection(info)
  }

  /** SECTION: [WorkflowRuntimeTracer] specific methods. **/

  override fun onWorkflowSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    systemTraceState?.let {
      val sessionId = session.sessionId
      // We are tracing, so set up some initial components for this workflow.
      val shortName = "WKF$sessionId ${session.name}"
      val nameWithKey = "WKF$sessionId ${session.logName}"

      val parentPart = session.parent?.let { parentSession ->
        " parent:${parentSession.traceName}"
      } ?: ""
      val asyncSectionName = "$nameWithKey$parentPart"

      val atraceCookie = workflowRuntimeTraceContext.runtimeName.hashCode() * sessionId.toInt()
      it.workflowAsyncSections[sessionId] = NameAndCookie(asyncSectionName, atraceCookie)
      it.workflowShortNamesById[sessionId] = shortName

      safeTrace.beginAsyncSection(asyncSectionName, atraceCookie)

      // Reason for render pass if we are the root.
      if (session.isRootWorkflow) {
        // This could be the first thing that happens after a trace has finished, so check if we need
        // to reset it here.
        it.resetTraceCountsIfNotTracing()
        it.renderPassTriggerCount++
        safeTrace.beginSection(
          "CREATE_RENDER${it.renderPassTriggerCount}, Runner:$workflowRuntimeTraceContext.runtimeName"
        )
        // These are both short, but CAUSE: is long in the regular case, so leave it as a separate
        // info section.
        infoSection("SUM:${it.renderPassTriggerCount}: Skipped:N, StateChange:Y")
        infoSection("CAUSE:${it.renderPassTriggerCount}: RootWFCreated:W($${session.name})")
      }
    }
  }

  override fun onWorkflowSessionStopped(
    sessionId: Long
  ) {
    systemTraceState?.let {
      val asyncSection = it.workflowAsyncSections.remove(sessionId)
      // TODO (RF-9493) Investigate asyncSection being null instead of ignoring the problem
      if (asyncSection != null) {
        safeTrace.endAsyncSection(asyncSection.name, asyncSection.cookie)
      }
    }
  }

  override fun onRuntimeUpdateEnhanced(
    runtimeUpdate: RuntimeUpdate,
    currentActionHandlingChangedState: Boolean,
    configSnapshot: ConfigSnapshot
  ) {
    if (!isSystemTraceable) return
    if (runtimeUpdate == RenderPassSkipped) {
      // Helps understanding traces.
      infoSection("CAUSE${renderPassNumber()} ${workflowRuntimeTraceContext.previousRenderCause}")
      // Skipping, end the section started when renderIncomingCause was set.
      safeTrace.endSection()
    }
    if (runtimeUpdate == RuntimeSettled) {
      // Build and add the summary!
      val summary = buildString {
        append("SUM${renderPassNumber()} ")
        append(configSnapshot.shortConfigAsString)
        append("StateChange:")
        if (currentActionHandlingChangedState) {
          append("Y, ")
        } else {
          append("N, ")
        }
      }
      infoSection(summary)
    }
  }

  override fun onRootPropsChanged(session: WorkflowSession) {
    if (systemTraceState != null) {
      safeTrace.beginSection(
        "PROPS_RENDER${++systemTraceState.renderPassTriggerCount}, Runner:${workflowRuntimeTraceContext.runtimeName}"
      )
      infoSection("SUM:${systemTraceState.renderPassTriggerCount}: Skipped:N, StateChange:Y")
      infoSection("CAUSE:${systemTraceState.renderPassTriggerCount}: RootWFProps:${session.name}")
    }
  }

  /** END SECTION: [WorkflowRuntimeTracer] specific methods. **/

  /** SECTION: [WorkflowInterceptor] override methods. **/

  override fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope,
    proceed: (P, Snapshot?, CoroutineScope) -> S,
    session: WorkflowSession
  ): S {
    return trace(
      systemTraceLabel = { "InitialState ${session.traceName}" },
    ) {
      proceed(props, snapshot, workflowScope)
    }
  }

  override fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S {
    return trace(
      systemTraceLabel = { "PropsChanged ${session.traceName}" },
    ) {
      proceed(old, new, state)
    }
  }

  override fun <P, R> onRenderAndSnapshot(
    renderProps: P,
    proceed: (P) -> RenderingAndSnapshot<R>,
    session: WorkflowSession
  ): RenderingAndSnapshot<R> {
    systemTraceState?.resetTraceCountsIfNotTracing()
    return trace(
      systemTraceLabel = {
        "RENDER${++systemTraceState!!.renderPassCount}" +
          " ${workflowRuntimeTraceContext.runtimeName}"
      },
    ) {
      proceed(renderProps).also {
        if (systemTraceState != null) {
          // Ends the section that's always started right when a [QueuedAction] is applied.
          safeTrace.endSection()
        }
      }
    }
  }

  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    val workflowName = session.traceName
    return trace(
      systemTraceLabel = { "Render $workflowName" }
    ) {
      proceed(
        renderProps,
        renderState,
        TracingRenderContextInterceptor(
          isRoot = session.isRootWorkflow,
          workflowName = workflowName
        )
      )
    }
  }

  override fun onSnapshotStateWithChildren(
    proceed: () -> TreeSnapshot,
    session: WorkflowSession
  ): TreeSnapshot {
    return trace(
      systemTraceLabel = { "Snapshot ${workflowRuntimeTraceContext.runtimeName}" }
    ) {
      proceed()
    }
  }

  /** END SECTION: [WorkflowInterceptor] override methods. **/

  /**
   * [RenderContextInterceptor] that adds Perfetto tracing.
   */
  private inner class TracingRenderContextInterceptor<P, S, O>(
    private val isRoot: Boolean,
    private val workflowName: String
  ) : RenderContextInterceptor<P, S, O> {

    override fun onActionSent(
      action: WorkflowAction<P, S, O>,
      proceed: (WorkflowAction<P, S, O>) -> Unit
    ) {
      val actionName = action.toLoggingShortName()
      val actionIndexLabel = "ACT${actionIndex++}"
      val traceActionName = if (isSystemTraceable) {
        "$actionIndexLabel A(${actionName.ifBlank { "" }})/W($workflowName)"
      } else {
        null
      }
      val queuedActionDetails = QueuedAction
      trace(
        systemTraceLabel = { "Send $traceActionName}" }
      ) {
        proceed(
          PerfettoTraceWorkflowAction(
            delegateAction = action,
            actionName = actionName,
            actionType = queuedActionDetails,
            actionIndex = actionIndexLabel,
          )
        )
      }
    }

    override fun onRunningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit,
      proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
    ) {
      val label = if (isSystemTraceable) {
        "EFF${effectIndex++} Key[$key]"
      } else {
        null
      }
      trace(
        systemTraceLabel = { "SideEffect $label" }
      ) {
        proceed(key, sideEffect)
      }
    }

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
      // onRenderChild is not traced (the child's own render will be traced),
      // but we trace the action handler.
      return proceed(child, childProps, key) { output ->
        val childOutputString = getWfLogString(output)
        trace(
          systemTraceLabel = { "Send Output[$childOutputString] to $workflowName" }
        ) {
          val delegateAction = handler(output)
          val actionName = delegateAction.toLoggingShortName()
          PerfettoTraceWorkflowAction(
            delegateAction = delegateAction,
            actionName = actionName,
            actionType = CascadeAction(
              childOutputString = childOutputString
            ),
          )
        }
      }
    }

    override fun <CResult> onRemember(
      key: String,
      resultType: KType,
      inputs: Array<out Any?>,
      calculation: () -> CResult,
      proceed: (
        key: String,
        resultType: KType,
        inputs: Array<out Any?>,
        calculation: () -> CResult
      ) -> CResult
    ): CResult {
      return trace(
        systemTraceLabel = { "Remember $key" }
      ) {
        proceed(key, resultType, inputs, calculation)
      }
    }

    /**
     * Class to trace the application of actions.
     */
    private inner class PerfettoTraceWorkflowAction<P, S, O>(
      private val delegateAction: WorkflowAction<P, S, O>,
      private val actionName: String,
      private val actionType: ActionType,
      private val actionIndex: String? = null
    ) : WorkflowAction<P, S, O>() {
      // Forward debugging name so we do not include anything about this tracing action.
      override val debuggingName: String
        get() = delegateAction.debuggingName

      /**
       * Trace application of the action.
       */
      override fun Updater.apply() {
        // See https://github.com/square/workflow-kotlin/issues/391. We have to listen to the 2nd
        // action in the cascade to get a useful ref on which Worker's handler was firing. This is
        // because Workers use an underlying Workflow and an intermediate action, which is the
        // QueuedAction, in their implementation. So yes, we use an implementation detail here to
        // detect that. The issue still tracks upstreaming this into the library.
        val isWorkerQueuedAction = actionName.contains(Worker.WORKER_OUTPUT_ACTION_NAME)
        if (actionType is QueuedAction) {
          if (isSystemTraceable) {
            safeTrace.beginSection(
              "MAYBE_RENDER${++systemTraceState!!.renderPassTriggerCount}:" +
                " $actionIndex," +
                " Runner:${workflowRuntimeTraceContext.runtimeName}"
            )
          }
        }
        val (_, actionApplied) = trace(
          systemTraceLabel = {
            val actionNameOrBlank = actionName.ifBlank { "" }
            val queuedApplyName = if (isWorkerQueuedAction) {
              "$workflowName(key=${actionNameOrBlank.workerKey()})"
            } else {
              actionNameOrBlank
            }
            if (actionType is CascadeAction) {
              "CascadeApply:$queuedApplyName," +
                " Cause:${workflowRuntimeTraceContext.renderIncomingCauses.lastOrNull()}"
            } else {
              "QueuedApply:$queuedApplyName"
            }
          },
        ) {
          delegateAction.applyTo(props, state).also { (newState, actionApplied) ->
            state = newState
            actionApplied.output?.let { setOutput(it.value) }
          }
        }

        if (isRoot || actionApplied.output == null) {
          // This action's application is ending a cascade, let's sum up what happened
          sumUpActionCascade()
        }
      }

      private fun sumUpActionCascade() {
        if (isSystemTraceable) {
          val causeLabel = buildString {
            append("CAUSE${renderPassNumber()} ")
            if (workflowRuntimeTraceContext.renderIncomingCauses.isEmpty()) {
              append("Unknown")
            } else {
              append(workflowRuntimeTraceContext.renderIncomingCauses.last())
            }
          }
          infoSection(causeLabel)
        }
      }
    }
  }

  /**
   * This method is inlined, so that when tracing is disabled there's no additional lambda creation.
   */
  private inline fun <T> trace(
    crossinline systemTraceLabel: () -> String,
    crossinline block: () -> T
  ): T {
    val systemTrace = isCurrentlySystemTracing
    if (systemTrace) {
      safeTrace.beginSection(systemTraceLabel())
    }
    try {
      return block()
    } finally {
      if (systemTrace) {
        safeTrace.endSection()
      }
    }
  }

  companion object {
    // Ensure index is unique across all workflow runtimes
    private var actionIndex = 0
    private var effectIndex = 0
  }
}
