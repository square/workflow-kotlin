package com.squareup.workflow1.tracing

import androidx.collection.MutableLongObjectMap
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession

/**
 * Context information about a workflow runtime that is tracked by [WorkflowRuntimeMonitor].
 */
public interface RuntimeTraceContext {
  /**
   * The name of the runtime.
   */
  public val runtimeName: String

  /**
   * A map of [WorkflowSessionInfo] that keeps track of active [WorkflowSession] keyed by the
   * sessionId (which is a Long).
   */
  public val workflowSessionInfo: MutableLongObjectMap<WorkflowSessionInfo>

  /**
   * Snapshot of the [RuntimeConfig].
   */
  public var configSnapshot: ConfigSnapshot

  /**
   * A list of all causes for the current runtime loop processing (it can be multiple in the case
   * of some optimizations).
   */
  public val renderIncomingCauses: MutableList<RenderCause>

  public var previousRenderCause: RenderCause?
  public var currentRenderCause: RenderCause?

  /**
   * Add an update into the [RuntimeUpdates] tracked by [WorkflowRuntimeMonitor]. See more
   * information about those at [RuntimeUpdateLogLine].
   *
   * Consider calling this from the `onNavigate` function you provide to
   * `reportNavigation`, e.g.
   *
   *         val renderings: Flow<Screen> by lazy {
   *           renderWorkflowIn(
   *             workflow = RootNavigationWorkflow,
   *             scope = viewModelScope,
   *             savedStateHandle = savedState,
   *             runtimeConfig = RuntimeConfigOptions.ALL
   *           ).reportNavigation {
   *             runtimeMonitor.addRuntimeUpdate(
   *               UiUpdateLogLine(getWfLogString(it))
   *             )
   *           }
   */
  public fun addRuntimeUpdate(event: RuntimeUpdateLogLine)
}
