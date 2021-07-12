@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

/**
 * A composable, optionally-stateful object that can [handle events][RenderContext.onEvent],
 * [delegate to children][RenderContext.renderChild], [subscribe][RenderContext.onWorkerOutput] to
 * arbitrary asynchronous events from the outside world.
 *
 * The basic purpose of a `Workflow` is to take some input (in the form of [PropsT]) and
 * return a [rendering][RenderingT]. To that end, a workflow may keep track of internal
 * [state][StatefulWorkflow], recursively ask other workflows to render themselves, subscribe to
 * data streams from the outside world, and handle events both from its
 * [renderings][RenderContext.onEvent] and from workflows it's delegated to (its "children"). A
 * `Workflow` may also emit [output events][OutputT] up to its parent `Workflow`.
 *
 * Workflows form a tree, where each workflow can have zero or more child workflows. Child workflows
 * are started as necessary whenever another workflow asks for them, and are cleaned up
 * automatically when they're no longer needed. [Props][PropsT] propagates down the tree,
 * [outputs][OutputT] and [renderings][RenderingT] propagate up the tree.
 *
 * ## Implementing `Workflow`
 *
 * The [Workflow] interface is useful as a facade for your API. You can publish an interface that
 * extends `Workflow`, and keep the implementation (e.g. is your workflow state*ful* or
 * state*less* a private implementation detail.
 *
 * You should almost never implement [Workflow] directly, however. There are two abstract classes
 * that you should subclass instead: [StatefulWorkflow] and [StatelessWorkflow]. The differences
 * between them are described below, but both type have a `render` method that you implement to
 * generate [renderings][RenderingT] from your [props][PropsT] and interact with the runtime (e.g.
 * by changing state or emitting outputs).
 *
 * ### [Stateful Workflows][StatefulWorkflow]
 *
 * If your workflow needs to keep track of internal state, subclass [StatefulWorkflow]. It has an
 * additional type parameter, `StateT`, requires you to specify
 * [how to create the initial state][StatefulWorkflow.initialState] and how to
 * [snapshot][StatefulWorkflow.snapshotState]/restore your state, and passes the current state to
 * the [StatefulWorkflow.render] method.
 *
 * ### [Stateless Workflows][StatelessWorkflow]
 *
 * If your workflow does not have any state of its own and simply needs to delegate to other
 * workflows (e.g. transforming props, outputs, or renderings), subclass [StatelessWorkflow] and
 * implement its sole [StatelessWorkflow.render] method, or just pass a lambda to the [stateless]
 * function.
 *
 * ## Interacting with events and other workflows
 *
 * All workflows are passed a [RenderContext] in their render methods. This context allows the
 * workflow to interact with the outside world by doing things like listening for events,
 * subscribing to streams of data, rendering child workflows, and performing cleanup when the
 * workflow is about to be torn down by its parent. See the documentation on [RenderContext] for
 * more information about what it can do.
 *
 * ## Things to avoid
 *
 * ### Mutable instance state
 *
 * Classes that implement [Workflow] should not contain mutable properties. Such properties are
 * instance-specific state and can introduce buggy behavior. Instead, subclass [StatefulWorkflow]
 * and move all your state to the workflow's `StateT` type. For example, setting a property will not
 * cause your workflow to be re-rendered – the runtime has no way of knowing that some data it
 * doesn't know about has changed. It can also break consumers of your workflows, who may be
 * expecting to be able to re-use the same instance of your workflow type in different places in the
 * workflow tree – this works if all the workflow's state is contained in its `StateT`, but not if
 * the instance has its own properties. _(Note that storing dependencies, which are effectively
 * static relative to the workflow instance, in properties is fine.)_
 *
 * ### Render side effects
 *
 * Workflows' `render` methods must not perform side effects or read mutable state. They can contain
 * logic, but the logic must be based on the [PropsT], the `StateT` if present, and the renderings
 * from other workflows. They must _declare_ what work to perform (via [Worker]s), and what data to
 * render (via [RenderingT]s and child workflows). For this reason, programming with workflows can
 * be considered declarative-style programming.
 *
 * @param PropsT Typically a data class that is used to pass configuration information or bits of
 * state that the workflow can always get from its parent and needn't duplicate in its own state.
 * May be [Unit] if the workflow does not need any props data.
 *
 * @param OutputT Typically a sealed class that represents "events" that this workflow can send
 * to its parent.
 * May be [Nothing] if the workflow doesn't need to emit anything.
 *
 * @param RenderingT The value returned to this workflow's parent during [composition][renderChild].
 * Typically represents a "view" of this workflow's props, current state, and children's renderings.
 * A workflow that represents a UI component may use a view model as its rendering type.
 *
 * @see StatefulWorkflow
 * @see StatelessWorkflow
 */
public interface Workflow<in PropsT, out OutputT, out RenderingT> {

  /**
   * Provides a [StatefulWorkflow] view of this workflow. Necessary because [StatefulWorkflow] is
   * the common API required for [RenderContext.renderChild] to do its work.
   */
  public fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT>

  /**
   * Empty companion serves as a hook point to allow us to create `Workflow.foo`
   * extension methods elsewhere.
   */
  public companion object
}

/**
 * Uses the given [function][transform] to transform a [Workflow] that
 * renders [FromRenderingT] to one renders [ToRenderingT],
 */
@OptIn(ExperimentalWorkflowApi::class)
public fun <PropsT, OutputT, FromRenderingT, ToRenderingT>
    Workflow<PropsT, OutputT, FromRenderingT>.mapRendering(
  transform: (FromRenderingT) -> ToRenderingT
): Workflow<PropsT, OutputT, ToRenderingT> =
  object : StatelessWorkflow<PropsT, OutputT, ToRenderingT>(), ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier get() = this@mapRendering.identifier

    override fun render(
      renderProps: PropsT,
      context: RenderContext
    ): ToRenderingT {
      val rendering = context.renderChild(this@mapRendering, renderProps) { output ->
        action({ "mapRendering" }) { setOutput(output) }
      }
      return transform(rendering)
    }

    override fun describeRealIdentifier(): String? =
      "${this@mapRendering.identifier}.mapRendering()"

    override fun toString(): String = "${this@mapRendering}.mapRendering()"
  }
