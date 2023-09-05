@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import com.squareup.workflow1.StatefulWorkflow.RenderContext
import com.squareup.workflow1.WorkflowAction.Companion.toString
import kotlinx.coroutines.CoroutineScope
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * A composable, stateful object that can [handle events][RenderContext.actionSink],
 * [delegate to children][RenderContext.renderChild], [subscribe][RenderContext.runningWorker] to
 * arbitrary asynchronous events from the outside world, and be [saved][snapshotState] to a
 * serialized form to be restored later.
 *
 * The basic purpose of a `Workflow` is to take some [props][PropsT] and return a
 * [rendering][RenderingT] that serves as a public representation of its current state,
 * and which can be used to update that state. A rendering typically serves as a view  model,
 * though this is not assumed, and is not the only use case.
 *
 * To that end, a workflow may keep track of internal [state][StateT],
 * recursively ask other workflows to render themselves, subscribe to data streams from the outside
 * world, and handle events both from its [renderings][RenderContext.actionSink] and from
 * workflows it's delegated to (its "children"). A `Workflow` may also emit
 * [output events][OutputT] up to its parent `Workflow`.
 *
 * Workflows form a tree, where each workflow can have zero or more child workflows. Child workflows
 * are started as necessary whenever another workflow asks for them, and are cleaned up
 * automatically when they're no longer needed. [Props][PropsT] propagate down the tree,
 * [outputs][OutputT] and [renderings][RenderingT] propagate up the tree.
 *
 * ## Avoid capturing stale state
 *
 * Workflows may not perform side effects in their `render` methods, but may perform side effects by
 * running [Worker]s and getting events from [RenderingT]s via [WorkflowAction]s. A [WorkflowAction]
 * defines how to update the [StateT] and what [OutputT]s to emit. Actions get access to the current
 * workflow's state, and they must use that view of the state. If an action is defined inline, it is
 * incorrect to capture, or close over, the [StateT] passed to [render] in the action. Workflows are
 * executed synchronously, but external events may not be, so captured state may be stale when the
 * action is invoked.
 *
 * @param PropsT Typically a data class that is used to pass configuration information or bits of
 * state that the workflow can always get from its parent and needn't duplicate in its own state.
 * May be [Unit] if the workflow does not need any props data.
 *
 * @param StateT Typically a data class that contains all of the internal state for this workflow.
 * The state is seeded via [props][PropsT] in [initialState]. It can be [serialized][snapshotState]
 * and later used to restore the workflow. **Implementations of the `Workflow`
 * interface should not generally contain their own state directly.** They may inject objects like
 * instances of their child workflows, or network clients, but should not contain directly mutable
 * state. This is the only type parameter that a parent workflow needn't care about for its children,
 * and may just use star (`*`) instead of specifying it. May be [Unit] if the workflow does not have
 * any internal state (see [StatelessWorkflow]).
 *
 * @param OutputT Typically a sealed class that represents "events" that this workflow can send
 * to its parent.
 * May be [Nothing] if the workflow doesn't need to emit anything.
 *
 * @param RenderingT The value returned to this workflow's parent during [composition][render].
 * Typically represents a "view" of this workflow's props, current state, and children's renderings.
 * A workflow that represents a UI component may use a view model as its rendering type.
 *
 * @see StatelessWorkflow
 */
public abstract class StatefulWorkflow<
  in PropsT,
  StateT,
  out OutputT,
  out RenderingT
  > : Workflow<PropsT, OutputT, RenderingT>, IdCacheable {

  public inner class RenderContext internal constructor(
    baseContext: BaseRenderContext<PropsT, StateT, OutputT>
  ) : BaseRenderContext<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT> by baseContext

  /**
   * Called from [RenderContext.renderChild] when the state machine is first started, to get the
   * initial state.
   *
   * @param snapshot
   * If the workflow is being created fresh, OR the workflow is being restored from a null or empty
   * [Snapshot], [snapshot] will be null. A snapshot is considered "empty" if [Snapshot.bytes]
   * returns an empty `ByteString`, probably because [snapshotState] returned `null`.
   * If the workflow is being restored from a [Snapshot], [snapshot] will be the last value
   * returned from [snapshotState], and implementations that return something other than
   * `null` should create their initial state by parsing their snapshot.
   */
  public abstract fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): StateT

  /**
   * @see [SessionWorkflow.initialState].
   * This method should only be used with a [SessionWorkflow]. It's just a pass through here so
   * that we can add this behavior for [SessionWorkflow] without disrupting all [StatefulWorkflow]s.
   */
  @WorkflowExperimentalApi
  public open fun initialState(
    props: PropsT,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope
  ): StateT = initialState(props, snapshot)

  /**
   * Called from [RenderContext.renderChild] instead of [initialState] when the workflow is already
   * running. This allows the workflow to detect changes in props, and possibly change its state in
   * response. This method is called only if the new props value is not `==` with the old.
   *
   * Default implementation does nothing.
   */
  public open fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: StateT
  ): StateT = state

  /**
   * Called at least once† any time one of the following things happens:
   *  - This workflow's [renderProps] changes (via the parent passing a different one in).
   *  - This workflow's [renderState] changes.
   *  - A descendant (immediate or transitive child) workflow:
   *    - Changes its internal state.
   *    - Emits an output.
   *
   * **Never call this method directly.** To nest the rendering of a child workflow in your own,
   * pass the child and any required props to [RenderContext.renderChild].
   *
   * This method *should not* have any side effects, and in particular should not do anything that
   * blocks the current thread. It may be called multiple times for the same state. It must do all its
   * work by calling methods on [context].
   *
   * _† This method is guaranteed to be called *at least* once for every state, but may be called
   * multiple times. Allowing this method to be invoked multiple times makes the internals simpler._
   */
  public abstract fun render(
    renderProps: PropsT,
    renderState: StateT,
    context: RenderContext
  ): RenderingT

  /**
   * Use a lazy delegate so that any [ImpostorWorkflow.realIdentifier] will have been computed
   * before this is initialized and cached.
   *
   * We use [LazyThreadSafetyMode.NONE] because access to these identifiers is thread-confined.
   */
  override var cachedIdentifier: WorkflowIdentifier? = null

  /**
   * Called whenever the state changes to generate a new [Snapshot] of the state.
   *
   * **Snapshots must be lazy.**
   *
   * Serialization must not be done at the time this method is called,
   * since the state will be snapshotted frequently but the serialized form may only be needed very
   * rarely.
   *
   * If the workflow does not have any state, or should always be started from scratch, return
   * `null` from this method.
   *
   * @see initialState
   */
  public abstract fun snapshotState(state: StateT): Snapshot?

  /**
   * Satisfies the [Workflow] interface by returning `this`.
   */
  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT> =
    this
}

/**
 * Creates a `RenderContext` from a [BaseRenderContext] for the given [StatefulWorkflow].
 */
@Suppress("UNCHECKED_CAST")
public fun <PropsT, StateT, OutputT, RenderingT> RenderContext(
  baseContext: BaseRenderContext<PropsT, StateT, OutputT>,
  workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext =
  (baseContext as? StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext)
    ?: workflow.RenderContext(baseContext)

/**
 * Returns a stateful [Workflow] implemented via the given functions.
 */
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.stateful(
  crossinline initialState: (PropsT, Snapshot?) -> StateT,
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
): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT> =
  object : StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>() {
    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ): StateT = initialState(props, snapshot)

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
 * Returns a stateful [Workflow], with no props, implemented via the given functions.
 */
public inline fun <StateT, OutputT, RenderingT> Workflow.Companion.stateful(
  crossinline initialState: (Snapshot?) -> StateT,
  crossinline render: BaseRenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT,
  crossinline snapshot: (StateT) -> Snapshot?
): StatefulWorkflow<Unit, StateT, OutputT, RenderingT> = stateful(
  { _, initialSnapshot -> initialState(initialSnapshot) },
  { _, state -> render(state) },
  snapshot
)

/**
 * Returns a stateful [Workflow] implemented via the given functions.
 *
 * This overload does not support snapshotting, but there are other overloads that do.
 */
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.stateful(
  crossinline initialState: (PropsT) -> StateT,
  crossinline render: BaseRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT,
  crossinline onPropsChanged: (
    old: PropsT,
    new: PropsT,
    state: StateT
  ) -> StateT = { _, _, state -> state }
): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT> = stateful(
  { props, _ -> initialState(props) },
  render,
  { null },
  onPropsChanged
)

/**
 * Returns a stateful [Workflow], with no props, implemented via the given function.
 *
 * This overload does not support snapshots, but there are others that do.
 */
public inline fun <StateT, OutputT, RenderingT> Workflow.Companion.stateful(
  initialState: StateT,
  crossinline render: BaseRenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT
): StatefulWorkflow<Unit, StateT, OutputT, RenderingT> = stateful(
  { initialState },
  { _, state -> render(state) }
)

/**
 * Convenience to create a [WorkflowAction] with parameter types matching those
 * of the receiving [StatefulWorkflow]. The action will invoke the given [lambda][update]
 * when it is [applied][WorkflowAction.apply].
 *
 * @param name A string describing the update for debugging, included in [toString].
 * @param update Function that defines the workflow update.
 */
public fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.action(
    name: String = "",
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
  ): WorkflowAction<PropsT, StateT, OutputT> = action({ name }, update)

/**
 * Convenience to create a [WorkflowAction] with parameter types matching those
 * of the receiving [StatefulWorkflow]. The action will invoke the given [lambda][update]
 * when it is [applied][WorkflowAction.apply].
 *
 * @param name Function that returns a string describing the update for debugging, included
 * in [toString].
 * @param update Function that defines the workflow update.
 */
public fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.action(
    name: () -> String,
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
  ): WorkflowAction<PropsT, StateT, OutputT> = object : WorkflowAction<PropsT, StateT, OutputT>() {
  override fun Updater.apply() = update.invoke(this)
  override fun toString(): String = "action(${name()})-${this@action}"
}
