package com.squareup.workflow1.tracing

import androidx.collection.LongObjectMap
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RuntimeUpdate
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope

/**
 * Interface for a pluggable [WorkflowRuntimeTracer] that can be used with the monitoring
 * provided by [WorkflowRuntimeMonitor]. Note that this extends [WorkflowInterceptor] to allow
 * [WorkflowRuntimeTracer]s to use those methods as well for tracing, except in a couple of
 * cases where it provides enhanced hooks, it prevents the underlying hooks from being used.
 */
public abstract class WorkflowRuntimeTracer : WorkflowInterceptor {

  protected lateinit var workflowRuntimeTraceContext: RuntimeTraceContext

  /**
   * Initialize the context for this tracer by attaching it to a [RuntimeTraceContext].
   *
   * This should not really be overridden (only [ChainedWorkflowRuntimeTracer] uses that
   * to chain this for all [WorkflowRuntimeTracer]).
   */
  public open fun attachRuntimeContext(
    workflowRuntimeTraceContext: RuntimeTraceContext
  ) {
    this.workflowRuntimeTraceContext = workflowRuntimeTraceContext
  }

  protected val sessionInfo: LongObjectMap<WorkflowSessionInfo>
    inline get() = workflowRuntimeTraceContext.workflowSessionInfo

  protected val WorkflowSession.name: String
    inline get() = requireNotNull(sessionInfo[sessionId]) {
      "Expected session info for sessionId $sessionId but found none."
    }.name

  protected val WorkflowSession.key: String
    inline get() = requireNotNull(sessionInfo[sessionId]) {
      "Expected session info for sessionId $sessionId but found none."
    }.key

  protected val WorkflowSession.logName: String
    inline get() = requireNotNull(sessionInfo[sessionId]) {
      "Expected session info for sessionId $sessionId but found none."
    }.logName

  protected val WorkflowSession.traceName: String
    inline get() = requireNotNull(sessionInfo[sessionId]) {
      "Expected session info for sessionId $sessionId but found none."
    }.traceName

  /**
   * The props passed into the root workflow have changed.
   */
  public open fun onRootPropsChanged(
    session: WorkflowSession,
  ) = Unit

  /**
   * A workflow session has started.
   */
  public open fun onWorkflowSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) = Unit

  /**
   * A workflow session has stopped.
   */
  public open fun onWorkflowSessionStopped(
    sessionId: Long
  ) = Unit

  /**
   * A [RuntimeUpdate] has occurred, we may want to trace something. This is to be preferred to
   * the [WorkflowInterceptor.onRuntimeUpdate] method as it provides more context about what is
   * currently happening in the runtime.
   */
  public open fun onRuntimeUpdateEnhanced(
    runtimeUpdate: RuntimeUpdate,
    currentActionHandlingChangedState: Boolean,
    configSnapshot: ConfigSnapshot,
  ) = Unit

  /** SECTION: [WorkflowInterceptor] overrides. */

  /**
   * Prevents [WorkflowRuntimeTracer]s from overriding this method, they should use
   * [onWorkflowSessionStarted] instead.
   */
  final override fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    super.onSessionStarted(workflowScope, session)
  }

  /**
   * Prevent [WorkflowRuntimeTracer] from overriding this function, as they should use
   * [onRuntimeUpdateEnhanced] instead.
   */
  final override fun onRuntimeUpdate(update: RuntimeUpdate) {
    super.onRuntimeUpdate(update)
  }

  /** END SECTION [WorkflowInterceptor] overrides. */
}
