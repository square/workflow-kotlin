package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.internal.compose.ComposeWorkflowNodeAdapter
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
): WorkflowNode<PropsT, OutputT, RenderingT> = when (workflow) {
  is ComposeWorkflow<*, *, *> -> {
    // Reuse the compose runtime if possible.
      ComposeWorkflowNodeAdapter(
        id = id,
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

internal abstract class WorkflowNode<PropsT, OutputT, RenderingT>(
  val id: WorkflowNodeId,
  protected val interceptor: WorkflowInterceptor,
  protected val emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult,
  baseContext: CoroutineContext,
) {

  /**
   * Scope that has a job that will live as long as this node and be cancelled when [cancel] is
   * called.
   * Also adds a debug name to this coroutine based on its ID.
   */
  val scope: CoroutineScope = CoroutineScope(
    baseContext +
      Job(parent = baseContext[Job]) +
      CoroutineName(id.toString())
  )

  /**
   * The [WorkflowSession] that represents this node to [WorkflowInterceptor]s.
   */
  abstract val session: WorkflowSession

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
   * Register select clauses for the next [result][ActionProcessingResult] from the state machine.
   *
   * Walk the tree of state machines, asking each one to wait for its next event. If something
   * happens that results in an output, that output is returned. Null means something happened that
   * requires a re-render, e.g. my state changed or a child state changed.
   *
   * It is an error to call this method after calling [cancel].
   *
   * Contrast this to [applyNextAvailableTreeAction], which is used to check for an action
   * that is already available without waiting, and then _immediately_ apply it.
   */
  abstract fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>)

  /**
   * Will try to apply any immediately available actions in this action queue or any of our
   * children's.
   *
   * Contrast this to [registerTreeActionSelectors] which will add select clauses that will await
   * the next action.
   *
   * @param skipDirtyNodes Whether or not this should skip over any workflow nodes that are already
   * 'dirty' - that is, they had their own state changed as the result of a previous action before
   * the next render pass.
   *
   * @return [ActionProcessingResult] of the action processed, or [ActionsExhausted] if there were
   * none immediately available.
   */
  abstract fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean = false): ActionProcessingResult

  /**
   * Cancels this state machine host, and any coroutines started as children of it.
   *
   * This must be called when the caller will no longer call [registerTreeActionSelectors]. It is an
   * error to call [registerTreeActionSelectors] after calling this method.
   */
  open fun cancel(cause: CancellationException? = null) {
    scope.cancel(cause)
  }
}
