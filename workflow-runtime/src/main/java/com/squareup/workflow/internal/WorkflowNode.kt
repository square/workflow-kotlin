/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.internal

import com.squareup.workflow.ExperimentalWorkflow
import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.Worker
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowSeed
import com.squareup.workflow.applyTo
import com.squareup.workflow.internal.RealRenderContext.WorkerRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalWorkflow::class)
internal fun <PropsT, StateT, OutputT : Any> startWorkflowNode(
  id: WorkflowNodeId,
  seed: WorkflowSeed<PropsT, StateT>,
  baseContext: CoroutineContext,
  runtime: WorkflowRuntime,
  emitOutputToParent: (OutputT) -> Any?
): WorkflowNode<PropsT, StateT, OutputT> {
  val workflowJob = Job(baseContext[Job])
  val context = baseContext + workflowJob + CoroutineName(id.toString())

  runtime.diagnosticListener?.apply {
    onWorkflowStarted(
        id.diagnosticId.id, id.diagnosticId.parentId, id.typeDebugString, id.name,
        seed.initialProps, seed.initialState, seed.restoredFromSnapshot
    )
    workflowJob.invokeOnCompletion {
      onWorkflowStopped(id.diagnosticId.id)
    }
  }

  return WorkflowNode(id, seed, context, runtime, emitOutputToParent)
}

/**
 * A node in a state machine tree. Manages the actual state for a given [Workflow].
 *
 * @param initialState Allows unit tests to start the node from a given state, instead of calling
 * [StatefulWorkflow.initialState].
 * @param coroutineContext Context that has a job that will live as long as this node. This context
 * will be used as the parent for all child workflow nodes and side effects.
 * @param emitOutputToParent A function that this node will call when it needs to emit an output
 * value to its parent. Returns either the output to be emitted from the root workflow, or null.
 */
@OptIn(ExperimentalWorkflow::class)
internal class WorkflowNode<PropsT, StateT, OutputT : Any>(
  val id: WorkflowNodeId,
  seed: WorkflowSeed<PropsT, StateT>,
  coroutineContext: CoroutineContext,
  private val runtime: WorkflowRuntime,
  private val emitOutputToParent: (OutputT) -> Any? = { it }
) : WorkerRunner<StateT, OutputT> {

  private val scope = CoroutineScope(coroutineContext)

  private val subtreeManager = SubtreeManager<StateT, OutputT>(
      coroutineContext, ::applyAction, id.diagnosticId, runtime, seed.snapshotCache
  )
  private val workers = ActiveStagingList<WorkerChildNode<*, *, *>>()
  private var state: StateT = seed.initialState
  private var lastProps: PropsT = seed.initialProps
  private val eventActionsChannel = Channel<WorkflowAction<StateT, OutputT>>(capacity = UNLIMITED)

  /**
   * Walk the tree of workflows, rendering each one and using
   * [RenderContext][com.squareup.workflow.RenderContext] to give its children a chance to
   * render themselves and aggregate those child renderings.
   */
  @Suppress("UNCHECKED_CAST")
  fun <RenderingT> render(
    workflow: StatefulWorkflow<PropsT, *, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT =
    renderWithStateType(workflow as StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>, input)

  /**
   * Walk the tree of state machines again, this time gathering snapshots and aggregating them
   * automatically.
   */
  fun snapshot(workflow: StatefulWorkflow<*, *, *, *>): Snapshot {
    @Suppress("UNCHECKED_CAST")
    val typedWorkflow = workflow as StatefulWorkflow<PropsT, StateT, OutputT, *>
    val childSnapshots = subtreeManager.createChildSnapshots()
    return createTreeSnapshot(
        rootSnapshot = typedWorkflow.snapshotState(state),
        childSnapshots = childSnapshots
    )
  }

  override fun <T> runningWorker(
    worker: Worker<T>,
    key: String,
    handler: (T) -> WorkflowAction<StateT, OutputT>
  ) {
    // Prevent duplicate workflows with the same key.
    workers.forEachStaging {
      require(!(it.matches(worker, key))) {
        "Expected keys to be unique for $worker: key=$key"
      }
    }

    // Start tracking this case so we can be ready to render it.
    val stagedWorker = workers.retainOrCreate(
        predicate = { it.matches(worker, key) },
        create = { createWorkerNode(worker, key, handler) }
    )
    stagedWorker.setHandler(handler)
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
  fun <T : Any> tick(selector: SelectBuilder<T?>) {
    // Listen for any child workflow updates.
    subtreeManager.tickChildren(selector)

    // Listen for any subscription updates.
    workers.forEachActive { child ->
      // Skip children that have finished but are still being run by the workflow.
      if (child.tombstone) return@forEachActive

      with(selector) {
        child.channel.onReceive { valueOrDone ->
          if (valueOrDone.isDone) {
            // Set the tombstone flag so we don't continue to listen to the subscription.
            child.tombstone = true
            // Nothing to do on close other than update the session, so don't emit any output.
            return@onReceive null
          } else {
            val update = child.acceptUpdate(valueOrDone.value)
            @Suppress("UNCHECKED_CAST")
            return@onReceive applyAction(update as WorkflowAction<StateT, OutputT>)
          }
        }
      }
    }

    // Listen for any events.
    with(selector) {
      eventActionsChannel.onReceive { action ->
        runtime.diagnosticListener?.onSinkReceived(id.diagnosticId.id, action)
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
  fun cancel(cause: CancellationException? = null) {
    // No other cleanup work should be done in this function, since it will only be invoked when
    // this workflow is *directly* discarded by its parent (or the host).
    // If you need to do something whenever this workflow is torn down, add it to the
    // invokeOnCompletion handler for the Job above.
    scope.cancel(cause)
  }

  /**
   * Contains the actual logic for [render], after we've casted the passed-in [Workflow]'s
   * state type to our `StateT`.
   */
  private fun <RenderingT> renderWithStateType(
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT {
    updatePropsAndState(workflow, props)

    val context = RealRenderContext(
        renderer = subtreeManager,
        workerRunner = this,
        eventActionsChannel = eventActionsChannel
    )
    runtime.diagnosticListener?.onBeforeWorkflowRendered(id.diagnosticId.id, props, state)
    val rendering = workflow.render(props, state, context)
    context.freeze()
    runtime.diagnosticListener?.onAfterWorkflowRendered(id.diagnosticId.id, rendering)

    // Tear down workflows and workers that are obsolete.
    subtreeManager.commitRenderedChildren()
    workers.commitStaging { it.channel.cancel() }

    return rendering
  }

  private fun updatePropsAndState(
    workflow: StatefulWorkflow<PropsT, StateT, OutputT, *>,
    newProps: PropsT
  ) {
    if (newProps != lastProps) {
      val newState = workflow.onPropsChanged(lastProps, newProps, state)
      runtime.diagnosticListener?.onPropsChanged(
          id.diagnosticId.id, lastProps, newProps, state, newState
      )
      state = newState
    }
    lastProps = newProps
  }

  /**
   * Applies [action] to this workflow's [state] and
   * [emits an output to its parent][emitOutputToParent] if necessary.
   */
  private fun <T : Any> applyAction(action: WorkflowAction<StateT, OutputT>): T? {
    val (newState, output) = action.applyTo(state)
    runtime.diagnosticListener?.onWorkflowAction(
        id.diagnosticId.id, action, state, newState, output
    )
    state = newState
    @Suppress("UNCHECKED_CAST")
    return output?.let(emitOutputToParent) as T?
  }

  private fun <T> createWorkerNode(
    worker: Worker<T>,
    key: String,
    handler: (T) -> WorkflowAction<StateT, OutputT>
  ): WorkerChildNode<T, StateT, OutputT> {
    val workerId = runtime.createDiagnosticId(id.diagnosticId)
    runtime.diagnosticListener?.onWorkerStarted(
        workerId.id, id.diagnosticId.id, key, worker.toString()
    )
    val workerChannel = scope.launchWorker(worker, key, workerId, runtime)
    return WorkerChildNode(worker, key, workerChannel, handler = handler)
  }
}
