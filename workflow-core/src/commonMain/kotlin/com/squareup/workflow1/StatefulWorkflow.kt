@file:JvmMultifileClass
@file:JvmName("Workflows")
@file:Suppress("ktlint:standard:indent")

package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import kotlinx.coroutines.CoroutineScope
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

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
  PropsT,
  StateT,
  OutputT,
  out RenderingT
  > : Workflow<PropsT, OutputT, RenderingT>, IdCacheable {

  public class RenderContext<PropsT, StateT, OutputT> internal constructor(
    baseContext: BaseRenderContext<PropsT, StateT, OutputT>
  ) : BaseRenderContext<PropsT, StateT, OutputT> by baseContext {
    @PublishedApi
    @OptIn(WorkflowExperimentalRuntime::class)
    internal val stableEventHandlers: Boolean =
      baseContext.runtimeConfig.contains(STABLE_EVENT_HANDLERS)

    /**
     * Creates a function which builds a [WorkflowAction] from the
     * given [update] function, and immediately passes it to [actionSink]. Handy for
     * attaching event handlers to renderings.
     *
     * It is important to understand that the [update] lambda you provide here
     * may not run synchronously. This function and its overloads provide a short cut
     * that lets you replace this snippet:
     *
     *    return SomeScreen(
     *      onClick = {
     *        context.actionSink.send(
     *          action("onClick") { state = SomeNewState }
     *        }
     *      }
     *    )
     *
     *  with this:
     *
     *    return SomeScreen(
     *      onClick = context.eventHandler("onClick") { state = SomeNewState }
     *    )
     *
     * Notice how your [update] function is passed to the [actionSink][BaseRenderContext.actionSink]
     * to be eventually executed as the body of a [WorkflowAction]. If several actions get stacked
     * up at once (think about accidental rapid taps on a button), that could take a while.
     *
     * If you require something to happen the instant a UI action happens, [eventHandler]
     * is the wrong choice. You'll want to write your own call to `actionSink.send`:
     *
     *    return SomeScreen(
     *      onClick = {
     *        // This happens immediately.
     *        MyAnalytics.log("SomeScreen was clicked")
     *
     *        context.actionSink.send(
     *          action("enter SomeNewState") {
     *            // This happens eventually.
     *            state = SomeNewState
     *          }
     *        }
     *      }
     *    )
     *
     * It is also important for Compose developers to understand that
     * a new function is created for a particular [eventHandler] call
     * each time [render] is called, which causes problems in Compose
     * UI code -- the lambdas from the current render pass will not be
     * `==` to those from the previous one, and unnecessary recomposition
     * will happen as a result. To prevent that problem set the [remember]
     * parameter to `true`, or set the [STABLE_EVENT_HANDLERS] option
     * in the [runtimeConfig].
     *
     * This problem will also be true of hand written handlers like
     * the `MyAnalytics.log` example above. Use the [BaseRenderContext.remember]
     * function to keep such bespoke lambdas stable. (This is what [eventHandler]
     * does when [remember] is true.)
     *
     *    val onClick = remember("onClick") {
     *      {
     *        MyAnalytics.log("SomeScreen was clicked")
     *
     *        context.actionSink.send(
     *          action("enter SomeNewState") {
     *            state = SomeNewState
     *          }
     *        )
     *      }
     *    }
     *
     *    return SomeScreen(onclick)
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
      update: Updater<PropsT, StateT, OutputT>.() -> Unit
    ): () -> Unit = eventHandler0(name, remember ?: stableEventHandlers, update)

    public inline fun <reified EventT> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, StateT, OutputT>.(EventT) -> Unit
    ): (EventT) -> Unit {
      val eh = eventHandler1(name, remember ?: stableEventHandlers, update)
      return eh
    }

    public inline fun <reified E1, reified E2> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, StateT, OutputT>.(E1, E2) -> Unit
    ): (E1, E2) -> Unit = eventHandler2(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2, reified E3> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, StateT, OutputT>.(E1, E2, E3) -> Unit
    ): (E1, E2, E3) -> Unit = eventHandler3(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2, reified E3, reified E4> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, StateT, OutputT>.(E1, E2, E3, E4) -> Unit
    ): (E1, E2, E3, E4) -> Unit = eventHandler4(name, remember ?: stableEventHandlers, update)

    public inline fun <reified E1, reified E2, reified E3, reified E4, reified E5> eventHandler(
      name: String,
      remember: Boolean? = null,
      noinline update: Updater<PropsT, StateT, OutputT>.(E1, E2, E3, E4, E5) -> Unit
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
      noinline update: Updater<PropsT, StateT, OutputT>.(
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
      noinline update: Updater<PropsT, StateT, OutputT>.(
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7) -> Unit {
      return eventHandler7(name, remember ?: stableEventHandlers, update)
    }

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
      noinline update: Updater<PropsT, StateT, OutputT>.(
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
      noinline update: Updater<PropsT, StateT, OutputT>.(
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
      noinline update: Updater<PropsT, StateT, OutputT>.(
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

    /**
     * Like [eventHandler], but no-ops if [state][WorkflowAction.Updater.state] has
     * changed to a different type than [CurrentStateT] by the time [update] fires.
     *
     * It is also important to understand that **even if [update] is called, there is
     * no guarantee that it will be called synchronously**. See [eventHandler] for more
     * details on that.
     *
     *    when(renderState) {
     *      is NewGame -> {
     *        NewGameScreen(
     *          onCancel = context.safeEventHandler<NewGame> {
     *            setOutput(CanceledStart)
     *          },
     *          onStartGame =
     *            context.safeEventHandler<NewGame, String, String> { currentState, x, o ->
     *              state = Playing(currentState.gameType, PlayerInfo(x, o))
     *            }
     *        )
     *      }
     *
     * This is not an uncommon case. Consider accidental rapid taps on
     * a button, where the first tap event moves the receiving [StatefulWorkflow]
     * to a new state. There is no reason to expect that the later taps will not
     * fire the (now stale) event handler a few more times. No promise can be
     * made that the [state][WorkflowAction.Updater.state] received by a [WorkflowAction]
     * will be of the same type as the `renderState` parameter that was received by
     * the [render] call that created it.
     *
     * @param CurrentStateT the subtype of [StateT] required by [update], which will not
     * be invoked if casting [state][WorkflowAction.Updater.state] to [CurrentStateT] fails.
     * @param name A string describing the handler for debugging.
     * @param onFailedCast Optional function invoked when casting fails. Default implementation
     * logs a warning with [println]
     * @param update Function that defines the workflow update.
     */
    public inline fun <reified CurrentStateT : StateT & Any> safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(currentState: CurrentStateT) -> Unit
    ): () -> Unit =
      eventHandler(name, remember) {
        CurrentStateT::class.safeCast(state)?.let { currentState -> this.update(currentState) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <reified CurrentStateT : StateT & Any, reified EventT> safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        event: EventT
      ) -> Unit
    ): (EventT) -> Unit =
      eventHandler(name, remember) { event ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, event) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2
      ) -> Unit
    ): (E1, E2) -> Unit =
      eventHandler(name, remember) { e1, e2 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3
      ) -> Unit
    ): (E1, E2, E3) -> Unit =
      eventHandler(name, remember) { e1, e2, e3 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4
      ) -> Unit
    ): (E1, E2, E3, E4) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3, e4) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4,
        e5: E5
      ) -> Unit
    ): (E1, E2, E3, E4, E5) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4, e5 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3, e4, e5) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4,
        e5: E5,
        e6: E6
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4, e5, e6 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3, e4, e5, e6) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4,
        e5: E5,
        e6: E6,
        e7: E7
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4, e5, e6, e7 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3, e4, e5, e6, e7) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      reified E8,
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4,
        e5: E5,
        e6: E6,
        e7: E7,
        e8: E8
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4, e5, e6, e7, e8 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3, e4, e5, e6, e7, e8) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
      reified E1,
      reified E2,
      reified E3,
      reified E4,
      reified E5,
      reified E6,
      reified E7,
      reified E8,
      reified E9,
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4,
        e5: E5,
        e6: E6,
        e7: E7,
        e8: E8,
        e9: E9
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4, e5, e6, e7, e8, e9 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState -> this.update(currentState, e1, e2, e3, e4, e5, e6, e7, e8, e9) }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }

    public inline fun <
      reified CurrentStateT : StateT & Any,
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
      > safeEventHandler(
      name: String,
      remember: Boolean? = null,
      crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
        ::defaultOnFailedCast,
      crossinline update: WorkflowAction<
        PropsT,
        StateT,
        OutputT
        >.Updater.(
        currentState: CurrentStateT,
        e1: E1,
        e2: E2,
        e3: E3,
        e4: E4,
        e5: E5,
        e6: E6,
        e7: E7,
        e8: E8,
        e9: E9,
        e10: E10
      ) -> Unit
    ): (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit =
      eventHandler(name, remember) { e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 ->
        CurrentStateT::class.safeCast(state)
          ?.let { currentState ->
            this.update(currentState, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10)
          }
          ?: onFailedCast(name, CurrentStateT::class, state)
      }
  }

  /**
   * Like [action], but no-ops if [state][WorkflowAction.Updater.state] has
   * changed to a different type than [CurrentStateT] by the time [update] fires.
   *
   *    private fun stopPlaying(
   *      game: CompletedGame
   *    ) = safeAction<Playing>("stopPlaying") { currentState ->
   *      state = when (game.ending) {
   *        Quitting -> MaybeQuitting(currentState.playerInfo, game)
   *        else -> GameOver(currentState.playerInfo, game)
   *      }
   *    }
   *
   * This is not an uncommon case. Consider accidental rapid taps on
   * a button, where the first tap event moves the receiving [StatefulWorkflow]
   * to a new state. There is no reason to expect that the later taps will not
   * fire the (now stale) event handler a few more times. No promise can be
   * made that the [state][WorkflowAction.Updater.state] received by a [WorkflowAction]
   * will be of the same type as the `renderState` parameter that was received by
   * the [render] call that created it.
   *
   * @param CurrentStateT the subtype of [StateT] required by [update], which will not
   * be invoked if casting [state][WorkflowAction.Updater.state] to [CurrentStateT] fails.
   * @param name A string describing the action for debugging.
   * @param onFailedCast Optional function invoked when casting fails. Default implementation
   * logs a warning with [println]
   * @param update Function that defines the workflow update.
   */
  public inline fun <reified CurrentStateT : StateT & Any> safeAction(
    name: String,
    crossinline onFailedCast: (name: String, type: KClass<*>, state: StateT) -> Unit =
      ::defaultOnFailedCast,
    noinline update: Updater<PropsT, StateT, OutputT>.(currentState: CurrentStateT) -> Unit
  ): WorkflowAction<PropsT, StateT, OutputT> = action({ name }) {
    CurrentStateT::class.safeCast(state)?.let { currentState -> this.update(currentState) }
      ?: onFailedCast(name, CurrentStateT::class, state)
  }

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
   * Called immediately before [render] if the parent workflow has provided updated
   * [PropsT] that are not `==` to those provided previously. This allows the child
   * to update its state based on the new information before rendering.
   *
   * Note, though, that it is generally a mistake to copy information from [PropsT]
   * to [StateT]! [PropsT] is always available via the `renderProps` parameter
   * of [render], and as [props][WorkflowAction.Updater.props] for a [WorkflowAction].
   * This method is mainly useful if you need to switch an `enum` or `sealed` [StateT]
   * based on [PropsT].
   *
   * Default implementation does nothing.
   *
   * @return the new [StateT]. [render] is then called immediately with this
   * value as `renderState`.
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
    context: RenderContext<PropsT, StateT, OutputT>
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

  companion object {

    @PublishedApi
    internal fun <StateT> defaultOnFailedCast(
      name: String,
      expectedType: KClass<*>,
      state: StateT
    ) {
      println("$name expected state of type ${expectedType.simpleName}, got $state")
    }
  }
}

/**
 * Creates a `RenderContext` from a [BaseRenderContext] for the given [StatefulWorkflow].
 */
@Suppress("UNCHECKED_CAST")
public fun <PropsT, StateT, OutputT, RenderingT> RenderContext(
  baseContext: BaseRenderContext<PropsT, StateT, OutputT>,
  workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>
): StatefulWorkflow.RenderContext<PropsT, StateT, OutputT> =
  (baseContext as? StatefulWorkflow.RenderContext<PropsT, StateT, OutputT>)
    ?: StatefulWorkflow.RenderContext<PropsT, StateT, OutputT>(baseContext)

/**
 * Returns a stateful [Workflow] implemented via the given functions.
 */
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.stateful(
  crossinline initialState: (PropsT, Snapshot?) -> StateT,
  crossinline render: StatefulWorkflow.RenderContext<PropsT, StateT, OutputT>.(
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
      context: RenderContext<PropsT, StateT, OutputT>
    ): RenderingT = render(context, renderProps, renderState)

    override fun snapshotState(state: StateT) = snapshot(state)
  }

/**
 * Returns a stateful [Workflow], with no props, implemented via the given functions.
 */
public inline fun <StateT, OutputT, RenderingT> Workflow.Companion.stateful(
  crossinline initialState: (Snapshot?) -> StateT,
  crossinline render: StatefulWorkflow.RenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT,
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
  crossinline render: StatefulWorkflow.RenderContext<PropsT, StateT, OutputT>.(
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
  crossinline render: StatefulWorkflow.RenderContext<Unit,
    StateT,
    OutputT>.(state: StateT) -> RenderingT
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
  name: String,
  update: Updater<PropsT, StateT, OutputT>.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = action({ name }, update)

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
public fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.action(
  name: () -> String,
  update: Updater<PropsT, StateT, OutputT>.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = object : WorkflowAction<PropsT, StateT, OutputT>() {
  override val debuggingName: String
    get() = name()

  override fun Updater.apply() = update.invoke(this)
}
