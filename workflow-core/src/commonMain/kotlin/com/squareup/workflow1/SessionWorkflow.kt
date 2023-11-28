package com.squareup.workflow1

import kotlinx.coroutines.CoroutineScope

/**
 * An extension of [StatefulWorkflow] that gives [initialState] a [CoroutineScope]
 * that corresponds with the lifetime of _session_ driven by this Workflow.
 *
 * A session begins the first time a parent passes a child [Workflow] of a particular type to
 * [renderChild] with a particular [key] parameter. It ends when the parent executes [render]
 * without making a matching [renderChild] call. The [CoroutineScope] that is passed to
 * [initialState] is created when a session starts (when [renderChild] is first called), and
 * [cancelled][kotlinx.coroutines.Job.cancel] when the session ends.
 *
 * This API extension exists on [StatefulWorkflow] as well, but it is confusing because the version
 * of [initialState] that does not have the [CoroutineScope] must also be implemented as it is
 * an abstract fun, even though it would never be used.
 * With this version, that confusion is removed and only the version of [initialState] with the
 * [CoroutineScope] must be implemented.
 */
@WorkflowExperimentalApi
public abstract class SessionWorkflow<
  in PropsT,
  StateT,
  out OutputT,
  out RenderingT
  > : StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>() {

  /**
   * @see [StatefulWorkflow.initialState] for kdoc on the base function of this method.
   *
   * This version adds the following:
   * @param workflowScope A [CoroutineScope] that has been created when this Workflow is first
   * rendered and canceled when it is no longer rendered.
   *
   * This [CoroutineScope] can be used to:
   *
   *  -  own the transforms on a [StateFlow][kotlinx.coroutines.flow.StateFlow],
   *     linking them to the lifetime of a Workflow session. For example,
   *     here is how you might safely combine two `StateFlow`s:
   *
   *     data class MyState(
   *       val derivedValue: String,
   *       val derivedWorker: Worker<String>
   *     )
   *
   *     override fun initialState(
   *       props: Unit,
   *       snapshot: Snapshot?,
   *       workflowScope: CoroutineScope
   *     ): MyState {
   *       val transformedStateFlow = stateFlow1.combine(stateFlow2, {val1, val2 -> val1 - val2}).
   *         stateIn(workflowScope, SharingStarted.Eagerly, ${stateFlow1.value}-${stateFlow2.value})
   *
   *       return MyState(
   *         transformedStateFlow.value,
   *         transformedStateFlow.asWorker()
   *       )
   *     }
   *
   *   - set reliable teardown hooks, e.g. via [Job.invokeOnCompletion][kotlinx.coroutines.Job.invokeOnCompletion].
   *     Note however, that while these are reliable in the sense of being guaranteed to be executed
   *     regardless of the lifetime of this workflow session, they are not reliable in that a
   *     completion handler on the Job is not thread-safe and will be executed on whatever the last
   *     dispatcher was used when the Job completes. See more on the [Job.invokeOnCompletion][kotlinx.coroutines.Job.invokeOnCompletion]
   *     kdoc.
   *
   *     So what do you do? Well, cleanup and lifecycle matters should be handled by each individual
   *     Worker and sideEffect. Consider using a try { } finally { cleanup() }
   *     or [Flow.onCompletion][kotlinx.coroutines.flow.onCompletion] to handle that.
   *
   *     If you have a general cleanup operation that is fast and thread-safe then you could use
   *     [Job.invokeOnCompletion][kotlinx.coroutines.Job.invokeOnCompletion].
   *
   * **Note Carefully**: Neither [workflowScope] nor any of these transformed/computed dependencies
   * should be stored by this Workflow instance. This could be re-created, or re-used unexpectedly
   * and should not have its own state. Instead, the transformed/computed dependencies must be
   * put into the [StateT] of this Workflow in order to be properly maintained.
   */
  public abstract override fun initialState(
    props: PropsT,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope
  ): StateT

  /**
   * Do not use this in favor of the version of [initialState] above that includes the Workflow's
   * [CoroutineScope]
   */
  public final override fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): StateT {
    error("SessionWorkflow should never call initialState without the CoroutineScope.")
  }
}

/**
 * Returns a [SessionWorkflow] implemented via the given functions.
 */
@WorkflowExperimentalApi
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.sessionWorkflow(
  crossinline initialState: (PropsT, Snapshot?, CoroutineScope) -> StateT,
  crossinline render: BaseRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT,
  crossinline snapshot: (StateT) -> Snapshot?,
  crossinline onPropsChanged: (
    old: PropsT,
    new: PropsT,
    state: StateT
  ) -> StateT = { _, _, state -> state }
): SessionWorkflow<PropsT, StateT, OutputT, RenderingT> =
  object : SessionWorkflow<PropsT, StateT, OutputT, RenderingT>() {
    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?,
      workflowScope: CoroutineScope
    ): StateT = initialState(props, snapshot, workflowScope)

    override fun onPropsChanged(
      old: PropsT,
      new: PropsT,
      state: StateT
    ): StateT = onPropsChanged(old, new, state)

    override fun render(
      renderProps: PropsT,
      renderState: StateT,
      context: RenderContext
    ): RenderingT = render(context, renderProps, renderState)

    override fun snapshotState(state: StateT) = snapshot(state)
  }

/**
 * Returns a [SessionWorkflow], with no props, implemented via the given functions.
 */
@WorkflowExperimentalApi
public inline fun <StateT, OutputT, RenderingT> Workflow.Companion.sessionWorkflow(
  crossinline initialState: (Snapshot?, CoroutineScope) -> StateT,
  crossinline render: BaseRenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT,
  crossinline snapshot: (StateT) -> Snapshot?
): SessionWorkflow<Unit, StateT, OutputT, RenderingT> = sessionWorkflow(
  { _, initialSnapshot, workflowScope -> initialState(initialSnapshot, workflowScope) },
  { _, state -> render(state) },
  snapshot
)

/**
 * Returns a [SessionWorkflow] implemented via the given functions.
 *
 * This overload does not support snapshotting, but there are other overloads that do.
 */
@WorkflowExperimentalApi
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.sessionWorkflow(
  crossinline initialState: (PropsT, CoroutineScope) -> StateT,
  crossinline render: BaseRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT,
  crossinline onPropsChanged: (
    old: PropsT,
    new: PropsT,
    state: StateT
  ) -> StateT = { _, _, state -> state }
): SessionWorkflow<PropsT, StateT, OutputT, RenderingT> = sessionWorkflow(
  { props, _, workflowScope -> initialState(props, workflowScope) },
  render,
  { null },
  onPropsChanged
)

/**
 * Returns a [SessionWorkflow], with no props, implemented via the given function.
 *
 * This overload does not support snapshots, but there are others that do.
 */
@WorkflowExperimentalApi
public inline fun <StateT, OutputT, RenderingT> Workflow.Companion.sessionWorkflow(
  crossinline initialState: (CoroutineScope) -> StateT,
  crossinline render: BaseRenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT
): SessionWorkflow<Unit, StateT, OutputT, RenderingT> = sessionWorkflow(
  { _, workflowScope -> initialState(workflowScope) },
  { _, state -> render(state) }
)
