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
@file:Suppress("DEPRECATION")
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow

import com.squareup.workflow.WorkflowAction.Updater

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
 * are started as necessary whenever another workflow asks for them, and are cleaned up automatically
 * when they're no longer needed. [Props][PropsT] propagate down the tree, [outputs][OutputT] and
 * [renderings][RenderingT] propagate up the tree.
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
abstract class StatefulWorkflow2<
    in PropsT,
    StateT,
    out OutputT,
    out RenderingT
    > : Workflow<PropsT, OutputT, RenderingT> {

  /**
   * Convenience for workflows that want to subclass [WorkflowAction] instead of using the
   * [action] helper.
   */
  abstract inner class WorkflowAction2 : WorkflowAction<PropsT, StateT, OutputT> {
    inner class Updater2(
      private val updater: Updater<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT>
    ) {
      var state: StateT
        get() = updater.state
        set(value) {
          updater.state = value
        }

      fun setOutput(output: @UnsafeVariance OutputT) = updater.setOutput(output)
    }

    abstract fun Updater2.apply()

    final override fun Updater<PropsT, StateT, OutputT>.apply() {
      with(Updater2(this)) { apply() }
    }
  }

  inner abstract class RenderContext2 {
    abstract val actionSink: Sink<WorkflowAction<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT>> /*=
      base.actionSink*/

    abstract fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String = "",
      onOutput: (ChildOutputT) -> WorkflowAction<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT>
    ): ChildRenderingT /*= base.renderChild(child, props, key, onOutput)*/

    abstract fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
    ) /*= base.runningSideEffect(key, sideEffect)*/
  }

  /**
   * Called from [RenderContext.renderChild] when the state machine is first started, to get the
   * initial state.
   *
   * @param snapshot
   * If the workflow is being created fresh, OR the workflow is being restored from a null or empty
   * [Snapshot], [snapshot] will be null. A snapshot is considered "empty" if [Snapshot.bytes]
   * returns an empty `ByteString`, probably because [snapshotState] returned [Snapshot.EMPTY].
   * If the workflow is being restored from a [Snapshot], [snapshot] will be the last value
   * returned from [snapshotState], and implementations that return something other than
   * [Snapshot.EMPTY] should create their initial state by parsing their snapshot.
   */
  abstract fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): StateT

  /**
   * Called from [RenderContext.renderChild] instead of [initialState] when the workflow is already
   * running. This allows the workflow to detect changes in props, and possibly change its state in
   * response. This method is called eagerly: `old` and `new` might be the same value, so it is up
   * to implementing code to perform any diffing if desired.
   *
   * Default implementation does nothing.
   */
  open fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: StateT
  ): StateT = state

  /**
   * Called at least once† any time one of the following things happens:
   *  - This workflow's [props] changes (via the parent passing a different one in).
   *  - This workflow's [state] changes.
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
  abstract fun render(
    props: PropsT,
    state: StateT,
    context: RenderContext2
  ): RenderingT

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
   * [Snapshot.EMPTY] from this method.
   *
   * @see initialState
   */
  abstract fun snapshotState(state: StateT): Snapshot?

  /**
   * Satisfies the [Workflow] interface by returning `this`.
   */
  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT> =
    object : StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>() {
      override fun initialState(
        props: PropsT,
        snapshot: Snapshot?
      ): StateT = this@StatefulWorkflow2.initialState(props, snapshot)

      override fun onPropsChanged(
        old: PropsT,
        new: PropsT,
        state: StateT
      ): StateT = this@StatefulWorkflow2.onPropsChanged(old, new, state)

      override fun render(
        props: PropsT,
        state: StateT,
        context: RenderContext<PropsT, StateT, OutputT>
      ): RenderingT {
        val context2 =
          object : StatefulWorkflow2<PropsT, StateT, OutputT, RenderingT>.RenderContext2() {
            override val actionSink: Sink<WorkflowAction<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT>>
              get() = context.actionSink

            override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
              child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
              props: ChildPropsT,
              key: String,
              onOutput: (ChildOutputT) -> WorkflowAction<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT>
            ): ChildRenderingT = context.renderChild(child, props, key, onOutput)

            override fun runningSideEffect(
              key: String,
              sideEffect: suspend () -> Unit
            ): Unit = context.runningSideEffect(key, sideEffect)
          }
        return this@StatefulWorkflow2.render(props, state, context2)
      }

      override fun snapshotState(state: StateT): Snapshot? =
        this@StatefulWorkflow2.snapshotState(state)
    }
}
