package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
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
public interface WorkflowInterceptor {

  /**
   * Called when the session is starting, before [onInitialState].
   *
   * @param workflowScope The [CoroutineScope] that will be used for any side effects the workflow
   * runs, as well as the parent for any workflows it renders.
   */
  public fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ): Unit = Unit

  /**
   * Intercepts calls to [StatefulWorkflow.initialState].
   */
  public fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    proceed: (P, Snapshot?) -> S,
    session: WorkflowSession
  ): S = proceed(props, snapshot)

  /**
   * Intercepts calls to [StatefulWorkflow.onPropsChanged].
   */
  public fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S = proceed(old, new, state)

  /**
   * Intercepts calls to [StatefulWorkflow.render].
   */
  public fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
    session: WorkflowSession
  ): R = proceed(renderProps, renderState, context)

  /**
   * Intercepts calls to [StatefulWorkflow.snapshotState].
   */
  public fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot?,
    session: WorkflowSession
  ): Snapshot? = proceed(state)

  /**
   * Information about the session of a workflow in the runtime that a [WorkflowInterceptor] method
   * is intercepting.
   */
  @ExperimentalWorkflowApi
  public interface WorkflowSession {
    /** The [WorkflowIdentifier] that represents the type of this workflow. */
    public val identifier: WorkflowIdentifier

    /**
     * The string key argument that was passed to [RenderContext.renderChild] to render this
     * workflow.
     */
    public val renderKey: String

    /**
     * A unique value that identifies the currently-running session of this workflow in the
     * runtime. See the documentation on [WorkflowInterceptor] for more information about what this
     * value represents.
     */
    public val sessionId: Long

    /** The parent [WorkflowSession] of this workflow, or null if this is the root workflow. */
    public val parent: WorkflowSession?
  }
}

/** A [WorkflowInterceptor] that does not intercept anything. */
@ExperimentalWorkflowApi
public object NoopWorkflowInterceptor : WorkflowInterceptor

/**
 * Returns a [StatefulWorkflow] that will intercept all calls to [workflow] via this
 * [WorkflowInterceptor].
 */
@OptIn(ExperimentalWorkflowApi::class)
internal fun <P, S, O, R> WorkflowInterceptor.intercept(
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
      renderProps: P,
      renderState: S,
      context: RenderContext
    ): R = onRender(
      renderProps, renderState, context,
        proceed = { p, s, c -> workflow.render(p, s, RenderContext(c, this)) },
        session = workflowSession
    )

    override fun snapshotState(state: S) =
      onSnapshotState(state, workflow::snapshotState, workflowSession)

    override fun toString(): String = "InterceptedWorkflow($workflow, $this@intercept)"
  }
}
