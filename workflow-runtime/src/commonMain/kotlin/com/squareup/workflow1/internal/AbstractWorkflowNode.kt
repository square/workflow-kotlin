package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

@OptIn(WorkflowExperimentalApi::class)
internal fun <PropsT, OutputT, RenderingT> createWorkflowNode(
  id: WorkflowNodeId,
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
): AbstractWorkflowNode<PropsT, OutputT, RenderingT> = when (workflow) {
  is ComposeWorkflow<*, *, *> -> ComposeWorkflowNode(
    id = id,
    workflow = workflow as ComposeWorkflow,
    initialProps = initialProps,
    baseContext = baseContext,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    emitAppliedActionToParent = emitAppliedActionToParent,
    parent = parent,
    interceptor = interceptor,
    idCounter = idCounter,
  )

  else -> StatefulWorkflowNode(
    id = id,
    workflow = workflow.asStatefulWorkflow(),
    initialProps = initialProps,
    snapshot = snapshot,
    baseContext = baseContext,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    emitAppliedActionToParent = emitAppliedActionToParent,
    parent = parent,
    interceptor = interceptor,
    idCounter = idCounter,
  )
}

internal abstract class AbstractWorkflowNode<PropsT, OutputT, RenderingT>(
  val id: WorkflowNodeId,
  final override val parent: WorkflowSession?,
  final override val workflowTracer: WorkflowTracer?,
  final override val runtimeConfig: RuntimeConfig,
  protected val interceptor: WorkflowInterceptor,
  protected val emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult,
  baseContext: CoroutineContext,
  idCounter: IdCounter?,
) : WorkflowSession,
  CoroutineScope {

  /**
   * Context that has a job that will live as long as this node.
   * Also adds a debug name to this coroutine based on its ID.
   */
  final override val coroutineContext = baseContext +
    Job(baseContext[Job]) +
    CoroutineName(id.toString())

  // WorkflowSession properties
  final override val identifier: WorkflowIdentifier get() = id.identifier
  final override val renderKey: String get() = id.name
  final override val sessionId: Long = idCounter.createId()

  final override fun toString(): String {
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
   *
   * @param workflow The "template" workflow instance used in the current render pass. This isn't
   * necessarily the same _instance_ every call, but will be the same _type_.
   */
  abstract fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT

  /**
   * Walk the tree of state machines again, this time gathering snapshots and aggregating them
   * automatically.
   */
  abstract fun snapshot(): TreeSnapshot

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
  abstract fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean

  /**
   * Cancels this state machine host, and any coroutines started as children of it.
   *
   * This must be called when the caller will no longer call [onNextAction]. It is an error to call [onNextAction]
   * after calling this method.
   */
  open fun cancel(cause: CancellationException? = null) {
    coroutineContext.cancel(cause)
  }
}
