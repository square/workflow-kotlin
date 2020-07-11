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
@file:Suppress("EXPERIMENTAL_API_USAGE")
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow

import com.squareup.workflow.WorkflowAction.Companion.noAction
import com.squareup.workflow.WorkflowAction.Updater
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
interface RenderContext<out PropsT, StateT, in OutputT> {

  /**
   * Accepts a single [WorkflowAction], invokes that action by calling [WorkflowAction.apply]
   * to update the current state, and optionally emits the returned output value if it is non-null.
   */
  val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>>

  @Deprecated("Use RenderContext.actionSink.")
  @Suppress("DEPRECATION")
  fun <EventT : Any> onEvent(
    handler: (EventT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): (EventT) -> Unit = EventHandler { event ->
    // Run the handler synchronously, so we only have to emit the resulting action and don't
    // need the update channel to be generic on each event type.
    val action = handler(event)
    actionSink.send(action)
  }

  /**
   * Creates a sink that will accept a single [WorkflowAction] of the given type.
   * Invokes that action by calling [WorkflowAction.apply] to update the current
   * state, and optionally emits the returned output value if it is non-null.
   */
  @Suppress("UNCHECKED_CAST", "DeprecatedCallableAddReplaceWith")
  @Deprecated("Use RenderContext.actionSink.")
  fun <A : WorkflowAction<PropsT, StateT, OutputT>> makeActionSink(): Sink<A> = actionSink

  /**
   * Ensures [child] is running as a child of this workflow, and returns the result of its
   * `render` method.
   *
   * **Never call [StatefulWorkflow.render] or [StatelessWorkflow.render] directly, always do it
   * through this context method.**
   *
   * 1. If the child _wasn't_ already running, it will be started either from
   *    [initialState][Workflow.initialState] or its snapshot.
   * 2. If the child _was_ already running, The workflow's
   *    [onInputChanged][StatefulWorkflow.onInputChanged] method is invoked with the previous input
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
  fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
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
  fun runningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit
  )
}

/**
 * Convenience alias of [RenderContext.renderChild] for workflows that don't take props.
 */
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, StateT, OutputT, ChildOutputT, ChildRenderingT>
    RenderContext<PropsT, StateT, OutputT>.renderChild(
  child: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  key: String = "",
  handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
): ChildRenderingT = renderChild(child, Unit, key, handler)
/* ktlint-enable parameter-list-wrapping */

/**
 * Convenience alias of [RenderContext.renderChild] for workflows that don't emit output.
 */
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, ChildPropsT, StateT, OutputT, ChildRenderingT>
    RenderContext<PropsT, StateT, OutputT>.renderChild(
  child: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
  key: String = ""
): ChildRenderingT = renderChild(child, props, key) { noAction() }
/* ktlint-enable parameter-list-wrapping */

/**
 * Convenience alias of [RenderContext.renderChild] for children that don't take props or emit
 * output.
 */
/* ktlint-disable parameter-list-wrapping */
fun <PropsT, StateT, OutputT, ChildRenderingT>
    RenderContext<PropsT, StateT, OutputT>.renderChild(
  child: Workflow<Unit, Nothing, ChildRenderingT>,
  key: String = ""
): ChildRenderingT = renderChild(child, Unit, key) { noAction() }
/* ktlint-enable parameter-list-wrapping */

/**
 * Ensures a [Worker] that never emits anything is running. Since [worker] can't emit anything,
 * it can't trigger any [WorkflowAction]s.
 *
 * A simple way to create workers that don't output anything is using [Worker.createSideEffect].
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
fun <PropsT, StateT, OutputT> RenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: Worker<Nothing>,
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
/* ktlint-disable parameter-list-wrapping */
inline fun <T, reified W : Worker<T>, PropsT, StateT, OutputT>
    RenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: W,
  key: String = "",
  noinline handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
/* ktlint-enable parameter-list-wrapping */
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
/* ktlint-disable parameter-list-wrapping */
internal fun <T, PropsT, StateT, OutputT>
    RenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: Worker<T>,
  workerType: KType,
  key: String = "",
  handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
/* ktlint-enable parameter-list-wrapping */
  val workerWorkflow = WorkerWorkflow<T>(workerType, key)
  renderChild(workerWorkflow, props = worker, key = key, handler = handler)
}

/**
 * Alternative to [RenderContext.actionSink] that allows externally defined
 * event types to be mapped to anonymous [WorkflowAction]s.
 */
fun <EventT, PropsT, StateT, OutputT> RenderContext<PropsT, StateT, OutputT>.makeEventSink(
  update: Updater<Any?, StateT, OutputT>.(EventT) -> Unit
): Sink<EventT> = actionSink.contraMap { event ->
  action({ "eventSink($event)" }) { update(event) }
}

/**
 * Ensures [worker] is running. When the [Worker] emits an output, [handler] is called
 * to determine the [WorkflowAction] to take. When the worker finishes, nothing happens (although
 * another render pass may be triggered).
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
@Deprecated(
    "Use runningWorker",
    ReplaceWith("runningWorker(worker, key, handler)", "com.squareup.workflow.runningWorker")
)
inline fun <PropsT, StateT, OutputT, reified T> RenderContext<PropsT, StateT, OutputT>.onWorkerOutput(
  worker: Worker<T>,
  key: String = "",
  noinline handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) = runningWorker(worker, key, handler)
