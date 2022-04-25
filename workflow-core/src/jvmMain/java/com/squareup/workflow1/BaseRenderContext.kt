@file:Suppress("EXPERIMENTAL_API_USAGE")
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowAction.Companion.noAction
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Facilities for a [Workflow] to interact with other [Workflow]s and the outside world from inside
 * a `render` function.
 *
 * ## Handling events from the UI
 *
 * While a workflow's rendering can represent whatever you need it to, it is common for the
 * rendering to contain the data for some part of your UI. In addition to shuttling data to the UI,
 * the rendering can also contain functions that the UI can call to send events to the workflow.
 *
 * E.g.
 * ```
 * data class Rendering(
 *   val radioButtonTexts: List<String>,
 *   val onSelected: (index: Int) -> Unit
 * )
 * ```
 *
 * To create populate such functions from your `render` method, you first need to define a
 * [WorkflowAction] to handle the event by changing state, emitting an output, or both. Then, just
 * pass a lambda to your rendering that instantiates the action and passes it to
 * [actionSink.send][Sink.send].
 *
 * ## Performing asynchronous work
 *
 * See [runningWorker].
 *
 * ## Composing children
 *
 * See [renderChild].
 */
public interface BaseRenderContext<out PropsT, StateT, in OutputT> {

  /**
   * Accepts a single [WorkflowAction], invokes that action by calling [WorkflowAction.apply]
   * to update the current state, and optionally emits the returned output value if it is non-null.
   */
  public val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>>

  /**
   * Ensures [child] is running as a child of this workflow, and returns the result of its
   * `render` method.
   *
   * **Never call [StatefulWorkflow.render] or [StatelessWorkflow.render] directly, always do it
   * through this context method.**
   *
   * 1. If the child _wasn't_ already running, it will be started either from
   *    [initialState][StatefulWorkflow.initialState] or its snapshot.
   * 2. If the child _was_ already running, The workflow's
   *    [onPropsChanged][StatefulWorkflow.onPropsChanged] method is invoked with the previous input
   *    and this one.
   * 3. The child's `render` method is invoked with `input` and the child's state.
   *
   * After this method returns, if something happens that trigger's one of `child`'s handlers, and
   * that handler emits an output, the function passed as [handler] will be invoked with that
   * output.
   *
   * @param key An optional string key that is used to distinguish between workflows of the same
   * type.
   */
  public fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String = "",
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT

  /**
   * Ensures [sideEffect] is running with the given [key].
   *
   * The first render pass in which this method is called, [sideEffect] will be launched in a new
   * coroutine that will be scoped to the rendering [Workflow]. Subsequent render passes that invoke
   * this method with the same [key] will not launch the coroutine again, but let it keep running.
   * Note that if a different function is passed with the same key, the side effect will *not* be
   * restarted, the new function will simply be ignored. The next render pass in which the
   * workflow does not call this method with the same key, the coroutine running [sideEffect] will
   * be
   * [cancelled](https://kotlinlang.org/docs/reference/coroutines/cancellation-and-timeouts.html).
   *
   * The coroutine will run with the same [CoroutineContext][kotlin.coroutines.CoroutineContext]
   * that the workflow runtime is running in. The side effect coroutine will not be started until
   * _after_ the first render call than runs it returns.
   *
   * @param key The string key that is used to distinguish between side effects.
   * @param sideEffect The suspend function that will be launched in a coroutine to perform the
   * side effect.
   */
  public fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  )

  // TODO(218): We'd prefer the eventHandler methods to be extensions, but the
  // compiler disagrees. https://youtrack.jetbrains.com/issue/KT-42741

  /**
   * Creates a function which builds a [WorkflowAction] from the
   * given [update] function, and immediately passes it to [actionSink]. Handy for
   * attaching event handlers to renderings.
   *
   * @param name A string describing the update, included in the action's [toString]
   * as a debugging aid
   * @param update Function that defines the workflow update.
   */
  public fun eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
  ): () -> Unit {
    return {
      actionSink.send(action(name, update))
    }
  }

  public fun <EventT> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(EventT) -> Unit
  ): (EventT) -> Unit {
    return { event ->
      actionSink.send(action(name) { update(event) })
    }
  }

  public fun <E1, E2> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2) -> Unit
  ): (E1, E2) -> Unit {
    return { e1, e2 ->
      actionSink.send(action(name) { update(e1, e2) })
    }
  }

  public fun <E1, E2, E3> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2, E3) -> Unit
  ): (E1, E2, E3) -> Unit {
    return { e1, e2, e3 ->
      actionSink.send(action(name) { update(e1, e2, e3) })
    }
  }

  public fun <E1, E2, E3, E4> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2, E3, E4) -> Unit
  ): (E1, E2, E3, E4) -> Unit {
    return { e1, e2, e3, e4 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4) })
    }
  }

  public fun <E1, E2, E3, E4, E5> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2, E3, E4, E5) -> Unit
  ): (E1, E2, E3, E4, E5) -> Unit {
    return { e1, e2, e3, e4, e5 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4, e5) })
    }
  }

  public fun <E1, E2, E3, E4, E5, E6> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2, E3, E4, E5, E6) -> Unit
  ): (E1, E2, E3, E4, E5, E6) -> Unit {
    return { e1, e2, e3, e4, e5, e6 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4, e5, e6) })
    }
  }

  public fun <E1, E2, E3, E4, E5, E6, E7> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2, E3, E4, E5, E6, E7) -> Unit
  ): (E1, E2, E3, E4, E5, E6, E7) -> Unit {
    return { e1, e2, e3, e4, e5, e6, e7 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4, e5, e6, e7) })
    }
  }

  public fun <E1, E2, E3, E4, E5, E6, E7, E8> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>.Updater.(E1, E2, E3, E4, E5, E6, E7, E8) -> Unit
  ): (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit {
    return { e1, e2, e3, e4, e5, e6, e7, e8 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4, e5, e6, e7, e8) })
    }
  }

  public fun <E1, E2, E3, E4, E5, E6, E7, E8, E9> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>
    .Updater.(E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit
  ): (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit {
    return { e1, e2, e3, e4, e5, e6, e7, e8, e9 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4, e5, e6, e7, e8, e9) })
    }
  }

  public fun <E1, E2, E3, E4, E5, E6, E7, E8, E9, E10> eventHandler(
    name: () -> String = { "eventHandler" },
    update: WorkflowAction<PropsT, StateT, OutputT>
    .Updater.(E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit
  ): (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit {
    return { e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 ->
      actionSink.send(action(name) { update(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10) })
    }
  }
}

/**
 * Convenience alias of [BaseRenderContext.renderChild] for workflows that don't take props.
 */
public fun <PropsT, StateT, OutputT, ChildOutputT, ChildRenderingT>
BaseRenderContext<PropsT, StateT, OutputT>.renderChild(
  child: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  key: String = "",
  handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
): ChildRenderingT = renderChild(child, Unit, key, handler)
/**
 * Convenience alias of [BaseRenderContext.renderChild] for workflows that don't emit output.
 */
public fun <PropsT, ChildPropsT, StateT, OutputT, ChildRenderingT>
BaseRenderContext<PropsT, StateT, OutputT>.renderChild(
  child: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
  key: String = ""
): ChildRenderingT = renderChild(child, props, key) { noAction() }
/**
 * Convenience alias of [BaseRenderContext.renderChild] for children that don't take props or emit
 * output.
 */
public fun <PropsT, StateT, OutputT, ChildRenderingT>
BaseRenderContext<PropsT, StateT, OutputT>.renderChild(
  child: Workflow<Unit, Nothing, ChildRenderingT>,
  key: String = ""
): ChildRenderingT = renderChild(child, Unit, key) { noAction() }
/**
 * Ensures a [Worker] that never emits anything is running. Since [worker] can't emit anything,
 * it can't trigger any [WorkflowAction]s.
 *
 * If your [Worker] does not output anything, then simply use [runningSideEffect].
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
public inline fun <reified W : Worker<Nothing>, PropsT, StateT, OutputT>
BaseRenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: W,
  key: String = ""
) {
  runningWorker(worker, key) {
    // The compiler thinks this code is unreachable, and it is correct. But we have to pass a lambda
    // here so we might as well check at runtime as well.
    @Suppress("UNREACHABLE_CODE", "ThrowableNotThrown")
    throw AssertionError("Worker<Nothing> emitted $it")
  }
}

/**
 * Ensures [worker] is running. When the [Worker] emits an output, [handler] is called
 * to determine the [WorkflowAction] to take. When the worker finishes, nothing happens (although
 * another render pass may be triggered).
 *
 * Like workflows, workers are kept alive across multiple render passes if they're the same type,
 * and different workers of distinct types can be run concurrently. However, unlike workflows,
 * workers are compared by their _declared_ type, not their actual type. This means that if you
 * pass a worker stored in a variable to this function, the type that will be used to compare the
 * worker will be the type of the variable, not the type of the object the variable refers to.
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
@OptIn(ExperimentalStdlibApi::class)
public inline fun <T, reified W : Worker<T>, PropsT, StateT, OutputT>
BaseRenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: W,
  key: String = "",
  noinline handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
  runningWorker(worker, typeOf<W>(), key, handler)
}

/**
 * Ensures [worker] is running. When the [Worker] emits an output, [handler] is called
 * to determine the [WorkflowAction] to take. When the worker finishes, nothing happens (although
 * another render pass may be triggered).
 *
 * @param workerType `typeOf<W>()`
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
@OptIn(ExperimentalStdlibApi::class)
@PublishedApi
internal fun <T, PropsT, StateT, OutputT>
BaseRenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: Worker<T>,
  workerType: KType,
  key: String = "",
  handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
  val workerWorkflow = WorkerWorkflow<T>(workerType, key)
  renderChild(workerWorkflow, props = worker, key = key, handler = handler)
}
