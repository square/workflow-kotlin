@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import com.squareup.workflow1.Worker.Companion.create
import com.squareup.workflow1.Worker.Companion.from
import com.squareup.workflow1.Worker.Companion.fromNullable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.experimental.ExperimentalTypeInference
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Represents a unit of asynchronous work that can have zero, one, or multiple outputs.
 *
 * Workers allow you to execute arbitrary, possibly asynchronous tasks in a declarative manner. To
 * perform their tasks, workers return a [Flow]. Workers are effectively [Flow]s that can be
 * [compared][doesSameWorkAs] to determine equivalence between render passes. A [Workflow] uses
 * Workers to perform asynchronous work during the render pass by calling
 * [BaseRenderContext.runningWorker].
 *
 * See the documentation on [run] for more information on the returned [Flow] is consumed and how
 * to implement asynchronous work.
 *
 * See the documentation on [doesSameWorkAs] for more details on how and when workers are compared
 * and the worker lifecycle.
 *
 * ## Example: Network request
 *
 * Let's say you have a network service with an API that returns a number, and you want to
 * call that service from a [Workflow].
 *
 * ```
 * interface TimeService {
 *   suspend fun getTime(timezone: String): Long
 * }
 * ```
 *
 * The first step is to define a Worker that can call this service, and maybe an extension
 * function on your service class:
 * ```
 * fun TimeService.getTimeWorker(timezone: String): Worker<Long> = TimeWorker(timezone, this)
 *
 * private class TimeWorker(
 *   val timezone: String,
 *   val service: TimeService
 * ): Worker<Long> {
 *
 *   override fun run(): Flow<Long> = flow {
 *     val time = service.getTime(timezone)
 *     emit(time)
 *   }
 * }
 * ```
 *
 * You also need to define how to determine if a previous Worker is already doing the same work.
 * This will ensure that if the same request is made by the same [Workflow] in adjacent render
 * passes, we'll keep the request alive from the first pass.
 * ```
 *   override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
 *     otherWorker is TimeWorker &&
 *       timezone == otherWorker.timezone
 * ```
 *
 * Now you can request the time from your [Workflow]:
 * ```
 * class MyWorkflow(private val timeWorker: TimeWorker) {
 *   override fun render(…): Foo {
 *     context.runningWorker(timeWorker) { time -> emitOutput("The time is $time") }
 *   }
 * ```
 *
 * Alternatively, if the response is a unique type, unlikely to be shared by any other workers,
 * you don't even need to create your own Worker class, you can use a builder, and the worker
 * will automatically be distinguished by that response type:
 * ```
 * interface TimeService {
 *   fun getTime(timezone: String): Deferred<TimeResponse>
 * }
 *
 * fun TimeService.getTimeWorker(timezone: String): Worker<TimeResponse> =
 *   Worker.from { getTime(timezone).await()) }
 * ```
 *
 * @see create
 * @see from
 * @see fromNullable
 */
public interface Worker<out OutputT> {

  /**
   * Returns a [Flow] to execute the work represented by this worker.
   *
   * [Flow] is "a cold asynchronous data stream that sequentially emits values and completes
   * normally or with an exception", although it may not emit any values. It is common to use
   * workers to perform some side effect that should only be executed when a state is entered – in
   * this case, the worker never emits anything (and will have type `Worker<Nothing>`).
   *
   * ## Coroutine Context
   *
   * When a worker is started, a coroutine is launched to [collect][Flow.collect] the flow.
   * When the worker is torn down, the coroutine is cancelled.
   * This coroutine is launched in the same scope as the workflow runtime, with a few changes:
   *
   * - The dispatcher is always set to [Unconfined][kotlinx.coroutines.Dispatchers.Unconfined] to
   *   minimize overhead for workers that don't care which thread they're executed on (e.g. logging
   *   side effects, workers that wrap third-party reactive libraries, etc.). If your work cares
   *   which thread it runs on, use [withContext][kotlinx.coroutines.withContext] or
   *   [flowOn][kotlinx.coroutines.flow.flowOn] to specify a dispatcher.
   * - A [CoroutineName][kotlinx.coroutines.CoroutineName] that describes the `Worker` instance
   *   (via `toString`) and the key specified by the workflow running the worker.
   *
   * ## Exceptions
   *
   * If a worker needs to report an error to the workflow running it, it *must not* throw it as an
   * exception – any exceptions thrown by a worker's [Flow] will cancel the entire workflow runtime.
   * Instead, the worker's [OutputT] type should be capable of expressing errors itself, and the
   * worker's logic should wrap any relevant exceptions into an output value (e.g. using the
   * [catch][kotlinx.coroutines.flow.catch] operator).
   *
   * While this might seem restrictive, this design decision keeps the
   * [BaseRenderContext.runningWorker] API simpler, since it does not need to handle exceptions
   * itself. It also discourages the code smell of relying on exceptions to handle control flow.
   */
  public fun run(): Flow<OutputT>

  /**
   * Override this method to define equivalence between [Worker]s. The default implementation
   * returns true if this worker's class is the same as [otherWorker]'s class.
   *
   * At the end of every render pass, the set of [Worker]s that were requested by the workflow are
   * compared to the set from the last render pass using this method. Workers are compared by their
   * _declared_ [KType] - including generics. Equivalent workers are allowed to keep running.
   * New workers are started ([run] is called and the returned [Flow] is collected). Old workers are
   * cancelled by cancelling their collecting coroutines. Workers for which [doesSameWorkAs] returns
   * false will also be restarted.
   *
   * Implementations of this method should not be based on object identity. Nor do they need to be
   * based on anything including in the [KType] - such as generics - as those will already be
   * compared by the Workflow Runtime, see [WorkerWorkflow].
   * For example, a [Worker] that performs a network request might check that two workers are
   * requests to the same endpoint and have the same request data.
   *
   * Most implementations of this method should compare constructor parameters.
   *
   * E.g:
   *
   * ```
   * class SearchWorker(private val query: String): Worker<SearchResult> {
   *   // run omitted for example.
   *
   *   override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
   *     otherWorker is SearchWorker && otherWorker.query == query
   * }
   * ```
   */
  public fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = otherWorker::class == this::class

  public companion object {

    /**
     * Use this value instead of calling `typeOf<Nothing>()` directly, which isn't allowed because
     * [Nothing] isn't allowed as a reified type parameter.
     *
     * The KType of Nothing on the JVM is actually java.lang.Void if you do
     * Nothing::class.createType(). However createType() lives in the reflection library, so we just
     * reference Void directly so we don't have to add a dependency on kotlin-reflect.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private val TYPE_OF_NOTHING = typeOf<Void>()

    /**
     * Shorthand for `flow { block() }.asWorker()`.
     *
     * Note: If your worker just needs to perform side effects and doesn't need to emit anything,
     * do not use a [Worker] but instead call [BaseRenderContext::runningSideEffect]
     */
    @OptIn(ExperimentalTypeInference::class)
    public inline fun <reified OutputT> create(
      @BuilderInference noinline block: suspend FlowCollector<OutputT>.() -> Unit
    ): Worker<OutputT> = flow(block).asWorker()

    /**
     * Creates a [Worker] that just performs some side effects and doesn't emit anything. Run the
     * worker from your `render` method using [BaseRenderContext.runningWorker].
     *
     * E.g.:
     * ```
     * fun logOnEntered(message: String) = Worker.createSideEffect() {
     *   println("Entered state: $message")
     * }
     * ```
     *
     * Note that all workers created with this method are equivalent from the point of view of
     * their [Worker.doesSameWorkAs] methods. A workflow that needs multiple simultaneous
     * side effects can either bundle them all together into a single `createSideEffect`
     * call, or can use the `key` parameter to [BaseRenderContext.runningWorker] to prevent
     * conflicts.
     *
     * Deprecated: This convenience extension is deprecated as redundant.
     * [BaseRenderContext.runningSideEffect] can be used instead with a suspend function
     * and a key to uniquely identify the side effect in the runtime.
     */
    @Deprecated(
      message = "Worker not needed, simply call RenderContext.runningSideEffect " +
        "with a suspend fun.",
      ReplaceWith(
        expression = "runningSideEffect(key, block)"
      )
    )
    public fun createSideEffect(
      block: suspend () -> Unit
    ): Worker<Nothing> = TypedWorker(TYPE_OF_NOTHING, flow { block() })

    /**
     * Returns a [Worker] that finishes immediately without emitting anything.
     */
    public fun <T> finished(): Worker<T> = FinishedWorker

    /**
     * Creates a [Worker] from a function that returns a single value.
     *
     * Shorthand for `flow { emit(block()) }.asWorker()`.
     *
     * The returned [Worker] will equate to any other workers created with any of the [Worker]
     * builder functions that have the same output type.
     */
    @OptIn(FlowPreview::class)
    public inline fun <reified OutputT> from(
      noinline block: suspend () -> OutputT
    ): Worker<OutputT> = block.asFlow().asWorker()

    /**
     * Creates a [Worker] from a function that returns a single value.
     * The worker will emit the value **if and only if the value is not null**, then finish.
     *
     * The returned [Worker] will equate to any other workers created with any of the [Worker]
     * builder functions that have the same output type.
     */
    public inline fun <reified OutputT> fromNullable(
      // This could be crossinline, but there's a coroutines bug that will cause the coroutine
      // to immediately resume on suspension inside block when it is crossinline.
      // See https://youtrack.jetbrains.com/issue/KT-31197.
      noinline block: suspend () -> OutputT?
    ): Worker<OutputT> = create {
      block()?.let { emit(it) }
    }

    /**
     * Creates a [Worker] that will emit [Unit] and then finish after [delayMs] milliseconds.
     * Negative delays are clamped to zero.
     *
     * Workers returned by this function will be compared by [key].
     */
    public fun timer(
      delayMs: Long,
      key: String = ""
    ): Worker<Unit> = TimerWorker(delayMs, key)
  }
}

/**
 * Returns a [Worker] that will, when performed, emit whatever this [Flow] receives.
 */
@OptIn(ExperimentalStdlibApi::class)
public inline fun <reified OutputT> Flow<OutputT>.asWorker(): Worker<OutputT> =
  TypedWorker(typeOf<OutputT>(), this)

/**
 * Returns a [Worker] that transforms this [Worker]'s [Flow] by calling [transform].
 *
 * The returned worker is considered equivalent with any other worker returned by this function
 * with the same receiver.
 *
 * ## Examples
 *
 * ### Workers from the same source are equivalent
 *
 * ```
 * val secondsWorker = millisWorker.transform {
 *   it.map { millis -> millis / 1000 }.distinctUntilChanged()
 * }
 *
 * val otherSecondsWorker = millisWorker.transform {
 *   it.map { millis -> millis.toSeconds() }
 * }
 *
 * assert(secondsWorker.doesSameWorkAs(otherSecondsWorker))
 * ```
 *
 * ### Workers from different sources are not equivalent
 *
 * ```
 * val secondsWorker = millisWorker.transform {
 *   it.map { millis -> millis / 1000 }.distinctUntilChanged()
 * }
 *
 * val otherSecondsWorker = secondsWorker.transform { it }
 *
 * assert(!secondsWorker.doesSameWorkAs(otherSecondsWorker))
 * ```
 */
public fun <T, R> Worker<T>.transform(
  transform: (Flow<T>) -> Flow<R>
): Worker<R> = WorkerWrapper(
  wrapped = this,
  flow = transform(run())
)

/**
 * A generic [Worker] implementation that defines equivalent workers as those having equivalent
 * [outputType]s. This is used by all the [Worker] builder functions.
 *
 * Note: We do not override the [doesSameWorkAs] definition here because the [outputType] [KType]
 * is already compared as part of the [KType] of the class itself in the Workflow runtime.
 */
@PublishedApi
internal class TypedWorker<OutputT>(
  private val outputType: KType,
  private val work: Flow<OutputT>
) : Worker<OutputT> {
  override fun run(): Flow<OutputT> = work
  override fun toString(): String = "TypedWorker($outputType)"
}

private data class TimerWorker(
  private val delayMs: Long,
  private val key: String
) : Worker<Unit> {

  override fun run() = flow {
    delay(delayMs)
    emit(Unit)
  }

  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
    otherWorker is TimerWorker && otherWorker.key == key
}

private object FinishedWorker : Worker<Nothing> {
  override fun run(): Flow<Nothing> = emptyFlow()
  override fun toString(): String = "FinishedWorker"
}

private data class WorkerWrapper<T, R>(
  private val wrapped: Worker<T>,
  private val flow: Flow<R>
) : Worker<R> {
  override fun run(): Flow<R> = flow
  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
    otherWorker is WorkerWrapper<*, *> &&
      wrapped.doesSameWorkAs(otherWorker.wrapped)

  override fun toString(): String = "WorkerWrapper($wrapped)"
}
