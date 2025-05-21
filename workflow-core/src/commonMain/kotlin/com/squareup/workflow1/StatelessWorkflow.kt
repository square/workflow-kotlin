@file:JvmMultifileClass
@file:JvmName("Workflows")
@file:Suppress("ktlint:standard:indent")

package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Minimal implementation of [Workflow] that maintains no state of its own.
 *
 * @param PropsT Typically a data class that is used to pass configuration information or bits of
 * state that the workflow can always get from its parent and needn't duplicate in its own state.
 * May be [Unit] if the workflow does not need any props data.
 *
 * @param OutputT Typically a sealed class that represents "events" that this workflow can send
 * to its parent.
 * May be [Nothing] if the workflow doesn't need to emit anything.
 *
 * @param RenderingT The value returned to this workflow's parent during [composition][render].
 * Typically represents a "view" of this workflow's props, current state, and children's renderings.
 * A workflow that represents a UI component may use a view model as its rendering type.
 *
 * @see StatefulWorkflow
 */
public abstract class StatelessWorkflow<PropsT, OutputT, RenderingT> :
  Workflow<PropsT, OutputT, RenderingT>, IdCacheable {

  @Suppress("UNCHECKED_CAST")
  public inner class RenderContext internal constructor(
    baseContext: BaseRenderContext<PropsT, *, OutputT>
  ) : BaseRenderContext<PropsT, Nothing, OutputT> by
  baseContext as BaseRenderContext<PropsT, Nothing, OutputT> {
    @PublishedApi
    @OptIn(WorkflowExperimentalRuntime::class)
    internal val stableEventHandlers: Boolean =
      baseContext.runtimeConfig.contains(STABLE_EVENT_HANDLERS)

    /**
     * Given an [update] function, wraps it in a lambda suitable for use
     * as an event handler on a rendered view model.
     * See [StatefulWorkflow.RenderContext.eventHandler] for details.
     *
     * @param name If [remember] is true, used as a unique key to distinguish
     * event handlers with same number and type of parameters. Also used
     * for descriptive logging and error messages.
     *
     * @param remember When true uses [RenderContext.remember] to ensure
     * that the same lambda is returned across multiple render passes,
     * allowing Compose to avoid unnecessary recomposition on updates.
     *
     * When false a new lambda is created on each call, and [name]
     * is used only for descriptive logging and error messages.
     *
     * When `null` a default value of `false` is used unless
     * [STABLE_EVENT_HANDLERS] has been specified in the [runtimeConfig].
     *
     * @throws IllegalArgumentException if [remember] is true and [name]
     * has already been used in the current [render] call for a lambda of
     * the same shape
     */
    public fun eventHandler(
      name: String,
      remember: Boolean? = null,
      update: Updater<PropsT, *, OutputT>.() -> Unit
    ): () -> Unit = eventHandler0(name, remember ?: stableEventHandlers, update)

    public inline fun <reified EventT> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(EventT) -> Unit
    ): (EventT) -> Unit = eventHandler1(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(E1, E2) -> Unit
    ): (E1, E2) -> Unit = eventHandler2(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2, reified E3> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(E1, E2, E3) -> Unit
    ): (E1, E2, E3) -> Unit = eventHandler3(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2, reified E3, reified E4> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(E1, E2, E3, E4) -> Unit
    ): (E1, E2, E3, E4) -> Unit = eventHandler4(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2, reified E3, reified E4, reified E5> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(E1, E2, E3, E4, E5) -> Unit
    ): (E1, E2, E3, E4, E5) -> Unit = eventHandler5(name, remember ?: stableEventHandlers, update)

    public inline fun <
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      > eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6) -> Unit =
      eventHandler6(name, remember ?: stableEventHandlers, update)

    public inline fun <
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      > eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7) -> Unit =
      eventHandler7(name, remember ?: stableEventHandlers, update)

    public inline fun <
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      reified E8,
      > eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
        E8,
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit =
      eventHandler8(name, remember ?: stableEventHandlers, update)

    public inline fun <
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      reified E8,
      reified E9,
      > eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
        E8,
        E9,
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit =
      eventHandler9(name, remember ?: stableEventHandlers, update)

    public inline fun <
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      reified E8,
      reified E9,
      reified E10,
      > eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, *, OutputT>.(
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
        E8,
        E9,
        E10,
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit =
      eventHandler10(name, remember ?: stableEventHandlers, update)
  }

  /**
   * Class type returned by [asStatefulWorkflow].
   * See [statefulWorkflow] for the instance.
   */
  private inner class StatelessAsStatefulWorkflow :
    StatefulWorkflow<PropsT, Unit, OutputT, RenderingT>() {

    /**
     * We want to cache the render context so that we don't have to recreate it each time
     * render() is called.
     */
    private var cachedStatelessRenderContext:
      StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext? = null

    /**
     * We must know if the RenderContext we are passed (which is a StatefulWorkflow.RenderContext)
     * has changed, so keep track of it.
     */
    private var canonicalStatefulRenderContext:
      StatefulWorkflow<PropsT, Unit, OutputT, RenderingT>.RenderContext? = null

    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ) = Unit

    override fun render(
      renderProps: PropsT,
      renderState: Unit,
      context: RenderContext
    ): RenderingT {
      // The `RenderContext` used *might* change - primarily in the case of our tests. E.g., The
      // `RenderTester` uses a special NoOp context to render twice to test for idempotency.
      // In order to support a changed render context but keep caching, we check to see if the
      // instance passed in has changed.
      if (cachedStatelessRenderContext == null || context !== canonicalStatefulRenderContext) {
        // Recreate it if the StatefulWorkflow.RenderContext we are passed has changed.
        cachedStatelessRenderContext = RenderContext(context, this@StatelessWorkflow)
      }
      canonicalStatefulRenderContext = context
      // Pass the StatelessWorkflow.RenderContext to our StatelessWorkflow.
      return render(renderProps, cachedStatelessRenderContext!!)
    }

    override fun snapshotState(state: Unit): Snapshot? = null
  }

  private val statefulWorkflow: StatefulWorkflow<PropsT, Unit, OutputT, RenderingT> =
    StatelessAsStatefulWorkflow()

  /**
   * Called at least once any time one of the following things happens:
   *  - This workflow's [renderProps] change (via the parent passing a different one in).
   *  - A descendant (immediate or transitive child) workflow:
   *    - Changes its internal state.
   *    - Emits an output.
   *
   * **Never call this method directly.** To get the rendering from a child workflow, pass the child
   * and any required props to [RenderContext.renderChild].
   *
   * This method *should not* have any side effects, and in particular should not do anything that
   * blocks the current thread. It may be called multiple times for the same state. It must do all its
   * work by calling methods on [context].
   */
  public abstract fun render(
    renderProps: PropsT,
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
   * Satisfies the [Workflow] interface by wrapping `this` in a [StatefulWorkflow] with `Unit`
   * state.
   */
  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT> =
    statefulWorkflow
}

/**
 * Creates a `RenderContext` from a [BaseRenderContext] for the given [StatelessWorkflow].
 */
@Suppress("UNCHECKED_CAST")
public fun <PropsT, OutputT, RenderingT> RenderContext(
  baseContext: BaseRenderContext<PropsT, *, OutputT>,
  workflow: StatelessWorkflow<PropsT, OutputT, RenderingT>
): StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext =
  (baseContext as? StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext)
    ?: workflow.RenderContext(baseContext)

/**
 * Returns a stateless [Workflow] via the given [render] function.
 *
 * Note that while the returned workflow doesn't have any _internal_ state of its own, it may use
 * [props][PropsT] received from its parent, and it may render child workflows that do have
 * their own internal state.
 */
public inline fun <PropsT, OutputT, RenderingT> Workflow.Companion.stateless(
  crossinline render: StatelessWorkflow<
    PropsT,
    OutputT,
    RenderingT
    >.RenderContext.(props: PropsT) -> RenderingT
): Workflow<PropsT, OutputT, RenderingT> =
  object : StatelessWorkflow<PropsT, OutputT, RenderingT>() {
    override fun render(
      renderProps: PropsT,
      context: RenderContext
    ): RenderingT = render(context, renderProps)
  }

/**
 * Returns a workflow that does nothing but echo the given [rendering].
 * Handy for testing.
 */
public fun <OutputT, RenderingT> Workflow.Companion.rendering(
  rendering: RenderingT
): Workflow<Unit, OutputT, RenderingT> = stateless { rendering }

/**
 * Convenience to create a [WorkflowAction] with parameter types matching those
 * of the receiving [StatefulWorkflow]. The action will invoke the given [lambda][update]
 * when it is [applied][WorkflowAction.apply].
 *
 * @param name A string describing the update for debugging, included in [toString].
 * @param update Function that defines the workflow update.
 */
public fun <PropsT, OutputT, RenderingT>
  StatelessWorkflow<PropsT, OutputT, RenderingT>.action(
  name: String,
  update: Updater<PropsT, *, OutputT>.() -> Unit
): WorkflowAction<PropsT, Nothing, OutputT> = action({ name }, update)

/**
 * Convenience to create a [WorkflowAction] with parameter types matching those
 * of the receiving [StatefulWorkflow]. The action will invoke the given [lambda][update]
 * when it is [applied][WorkflowAction.apply].
 *
 * @param name Function that returns a string describing the update for debugging, this will
 *  be returned by [WorkflowAction.debuggingName], which is in turn included in the default
 *  [WorkflowAction.toString].
 * @param update Function that defines the workflow update.
 */
@Suppress("UnusedReceiverParameter")
public fun <PropsT, OutputT, RenderingT>
  StatelessWorkflow<PropsT, OutputT, RenderingT>.action(
  name: () -> String,
  update: Updater<PropsT, *, OutputT>.() -> Unit
): WorkflowAction<PropsT, Nothing, OutputT> = object : WorkflowAction<PropsT, Nothing, OutputT>() {
  override val debuggingName: String
    get() = name()

  override fun Updater.apply() = update.invoke(this)
}
