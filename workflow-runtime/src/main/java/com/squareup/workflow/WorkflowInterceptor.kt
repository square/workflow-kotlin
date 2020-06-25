/*
 * Copyright 2020 Square Inc.
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
package com.squareup.workflow

import com.squareup.workflow.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope

/**
 * Provides hooks into the workflow runtime that can be used to instrument or modify the behavior
 * of workflows.
 *
 * This interface's methods mirror the methods of [StatefulWorkflow]. It also has one additional
 * method, [onSessionStarted], that is notified when a workflow is started. Each method returns the
 * same thing as the corresponding method on [StatefulWorkflow], and receives the same parameters
 * as well as two extra parameters:
 *
 *  - **`proceed`** – A function that _exactly_ mirrors the corresponding function on
 *    [StatefulWorkflow], accepting the same parameters and returning the same thing. An interceptor
 *    can call this function to run the actual workflow, but it may also decide to not call it at
 *    all, or call it multiple times.
 *  - **`session`** – A [WorkflowSession] object that can be queried for information about the
 *    workflow being intercepted.
 *
 * All methods have default no-op implementations.
 *
 * ## Workflow sessions
 *
 * A single workflow may be rendered by different parents at the same time, or the same parent at
 * different, disjoint times. Each continuous sequence of renderings of a particular workflow type,
 * with the same key passed to [RenderContext.renderChild], is called an "session" of that
 * workflow. The workflow's [StatefulWorkflow.initialState] method will be called at the start of
 * the session, and its state will be maintained by the runtime until the session is finished.
 * Each session is identified by the [WorkflowSession] object passed into the corresponding method
 * in a [WorkflowInterceptor].
 *
 * In addition to the [WorkflowIdentifier] of the type of the workflow being rendered, this object
 * also knows the [key][WorkflowSession.renderKey] used to render the workflow and the
 * [WorkflowSession] of the [parent][WorkflowSession.parent] workflow that is rendering it.
 *
 * Each session is also assigned a numerical ID that uniquely identifies the session over the
 * life of the entire runtime. This value will remain constant as long as the workflow's parent is
 * rendering it, and then it will never be used again. If this workflow stops being rendered, and
 * then starts again, the value will be different.
 */
@ExperimentalWorkflowApi
interface WorkflowInterceptor {

  /**
   * Called when the session is starting, before [onInitialState].
   *
   * @param workflowScope The [CoroutineScope] that will be used for any side effects the workflow
   * runs, as well as the parent for any workflows it renders.
   */
  fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) = Unit

  /**
   * Intercepts calls to [StatefulWorkflow.initialState].
   */
  fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    proceed: (P, Snapshot?) -> S,
    session: WorkflowSession
  ): S = proceed(props, snapshot)

  /**
   * Intercepts calls to [StatefulWorkflow.onPropsChanged].
   */
  fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S = proceed(old, new, state)

  /**
   * Intercepts calls to [StatefulWorkflow.render].
   */
  fun <P, S, O : Any, R> onRender(
    props: P,
    state: S,
    context: RenderContext<S, O>,
    proceed: (P, S, RenderContext<S, O>) -> R,
    session: WorkflowSession
  ): R = proceed(props, state, context)

  /**
   * Intercepts calls to [StatefulWorkflow.snapshotState].
   */
  fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot,
    session: WorkflowSession
  ): Snapshot = proceed(state)

  /**
   * Information about the session of a workflow in the runtime that a [WorkflowInterceptor] method
   * is intercepting.
   */
  @ExperimentalWorkflowApi
  interface WorkflowSession {
    /** The [WorkflowIdentifier] that represents the type of this workflow. */
    val identifier: WorkflowIdentifier

    /**
     * The string key argument that was passed to [RenderContext.renderChild] to render this
     * workflow.
     */
    val renderKey: String

    /**
     * A unique value that identifies the currently-running session of this workflow in the
     * runtime. See the documentation on [WorkflowInterceptor] for more information about what this
     * value represents.
     */
    val sessionId: Long

    /** The parent [WorkflowSession] of this workflow, or null if this is the root workflow. */
    val parent: WorkflowSession?
  }
}

/** A [WorkflowInterceptor] that does not intercept anything. */
@ExperimentalWorkflowApi
object NoopWorkflowInterceptor : WorkflowInterceptor

/**
 * Returns a [StatefulWorkflow] that will intercept all calls to [workflow] via this
 * [WorkflowInterceptor].
 */
@OptIn(ExperimentalWorkflowApi::class)
internal fun <P, S, O : Any, R> WorkflowInterceptor.intercept(
  workflow: StatefulWorkflow<P, S, O, R>,
  workflowSession: WorkflowSession
): StatefulWorkflow<P, S, O, R> = if (this === NoopWorkflowInterceptor) {
  workflow
} else {
  object : StatefulWorkflow<P, S, O, R>() {
    override fun initialState(
      props: P,
      snapshot: Snapshot?
    ): S = onInitialState(props, snapshot, workflow::initialState, workflowSession)

    override fun onPropsChanged(
      old: P,
      new: P,
      state: S
    ): S = onPropsChanged(old, new, state, workflow::onPropsChanged, workflowSession)

    override fun render(
      props: P,
      state: S,
      context: RenderContext<S, O>
    ): R = onRender(props, state, context, workflow::render, workflowSession)

    override fun snapshotState(state: S): Snapshot =
      onSnapshotState(state, workflow::snapshotState, workflowSession)

    override fun toString(): String = "InterceptedWorkflow($workflow, $this@intercept)"
  }
}
