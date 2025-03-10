@file:Suppress(
  "ktlint:standard:indent",
  "ktlint:standard:parameter-list-spacing",
  "ktlint:standard:parameter-wrapping",
)
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.WorkflowComposable
import com.squareup.workflow1.compose.renderWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
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

  public val runtimeConfig: RuntimeConfig

  /**
   * Accepts a single [WorkflowAction], invokes that action by calling [WorkflowAction.apply]
   * to update the current state, and optionally emits the returned output value if it is non-null.
   */
  public val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>>

  public val workflowTracer: WorkflowTracer?

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
   * Synchronously composes a [content] function and returns its rendering. Whenever [content] is
   * invalidated (i.e. a compose snapshot state object is changed that was previously read by
   * [content] or any functions it calls), this workflow will be re-rendered and the relevant
   * composables will be recomposed.
   *
   * To render child workflows from this method, call [renderWorkflow].
   * Any state saved using Compose's state restoration mechanism (e.g. [rememberSaveable]) will be
   * saved and restored using the workflow snapshot mechanism.
   *
   * @see ComposeWorkflow
   */
  @WorkflowExperimentalApi
  public fun <ChildRenderingT> renderComposable(
    key: String = "",
    content:
    @WorkflowComposable @Composable
    () -> ChildRenderingT
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
   * Note that there is currently an [issue](https://github.com/square/workflow-kotlin/issues/1093)
   * when a [runningSideEffect] (and thus also [runningWorker], or the parent Workflow of either
   * via [renderChild]) is declared as running (or rendering) in one render pass and
   * then not declared in the next render pass and both those consecutive render passes happen
   * synchronously - i.e. without the [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher]
   * for the Workflow runtime being able to dispatch asynchronously. This is because the jobs for
   * side effects are launched lazily in order to ensure they happen after the render pass, but if
   * the [CoroutineScope]'s job (the parent for all these jobs) is cancelled before these lazy
   * coroutines have a chance to dispatch, then they will never run at all. For more details, and
   * to report problems with this, see the [issue](https://github.com/square/workflow-kotlin/issues/1093).
   * If you need guaranteed execution for some code in this scenario (like for cleanup),
   * please use a [SessionWorkflow] and the [SessionWorkflow.initialState] that provides the
   * [CoroutineScope] which is equivalent to the lifetime of the Workflow node in the tree. The
   * [Job][kotlinx.coroutines.Job] can be extracted from that and used to get guaranteed to be
   * executed lifecycle hooks, e.g. via [Job.invokeOnCompletion][kotlinx.coroutines.Job.invokeOnCompletion].
   *
   * @param key The string key that is used to distinguish between side effects.
   * @param sideEffect The suspend function that will be launched in a coroutine to perform the
   * side effect.
   */
  public fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  )

  /**
   * Rather than calling this directly, prefer the inline extension function
   * which will capture [resultType] for you.
   *
   * Remember the value calculated by the [calculation] lambda.
   * The [calculation] will be run the first time, and on any subsequent render pass where the
   * [inputs] have changed.
   *
   * @param key used to distinguish between calculations of the same type
   * @param inputs any inputs to the [calculation]. The [calculation] will only rerun
   *  if any of these inputs changes, so make sure you use the exact list of inputs for
   *  the calculation here.
   * @param calculation a lambda that performs the calculation based on the [inputs].
   *
   * @throws IllegalArgumentException if [key] has already been used in the current
   * `render` call for a lambda of the same shape
   */
  public fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT
}

/**
 * Remember the value calculated by the [calculation] lambda. The [calculation]
 * will be run the first time, and on any subsequent render pass where the
 * [inputs] have changed.
 *
 * The [StatefulWorkflow.RenderContext.eventHandler] and
 * [StatelessWorkflow.RenderContext.eventHandler] functions use this
 * mechanism to provide lambdas whose identity are stable across
 * multiple render passes.
 *
 * @param key used to distinguish between calculations of the same type
 * @param inputs any inputs to the [calculation]. The [calculation] will only rerun
 *  if any of these inputs changes, so make sure you use the exact list of inputs for
 *  the calculation here.
 * @param calculation a lambda that performs the calculation based on the [inputs].
 *
 * @throws IllegalArgumentException if [key] has already been used in the current
 * `render` call for a lambda of the same shape
 */
public inline fun <reified ResultT> BaseRenderContext<*, *, *>.remember(
  key: String,
  vararg inputs: Any?,
  noinline calculation: () -> ResultT
): ResultT {
  return remember(key, typeOf<ResultT>(), inputs = inputs, calculation)
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
 * Ensures a [LifecycleWorker] is running. Since [worker] can't emit anything,
 * it can't trigger any [WorkflowAction]s.
 *
 * You may want to consider using [SessionWorkflow]. See note on [LifecycleWorker] and the docs
 * for [SessionWorkflow].
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
public inline fun <reified W : LifecycleWorker, PropsT, StateT, OutputT>
  BaseRenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: W,
  key: String = ""
) {
  runningWorker(worker, key) {
    // The compiler thinks this code is unreachable, and it is correct. But we have to pass a lambda
    // here so we might as well check at runtime as well.
    @Suppress("UNREACHABLE_CODE")
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
 * Note that there is currently an [issue](https://github.com/square/workflow-kotlin/issues/1093)
 * which can effect whether a [Worker] is ever executed.
 * See more details at [BaseRenderContext.runningSideEffect].
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
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
@PublishedApi
internal fun <T, PropsT, StateT, OutputT>
  BaseRenderContext<PropsT, StateT, OutputT>.runningWorker(
  worker: Worker<T>,
  workerType: KType,
  key: String = "",
  handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
  val workerWorkflow = workflowTracer.trace("CreateWorkerWorkflow") {
    WorkerWorkflow<T>(workerType, key)
  }
  renderChild(workerWorkflow, props = worker, key = key, handler = handler)
}
