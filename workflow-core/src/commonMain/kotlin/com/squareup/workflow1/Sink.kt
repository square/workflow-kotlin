@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * An object that receives values (commonly events or [WorkflowAction]).
 * [BaseRenderContext.actionSink] implements this interface.
 */
public fun interface Sink<in T> {
  public fun send(value: T)
}

/**
 * Generates a new sink of type [T2].
 *
 * Given a [transform] closure, the following code is functionally equivalent:
 *
 *    sink.send(transform(value))
 *
 *    sink.contraMap(transform).send(value)
 *
 *  **Trivia**: Why is this called `contraMap`?
 *     - `map` turns `Type<T>` into `Type<U>` via `(T)->U`.
 *     - `contraMap` turns `Type<T>` into `Type<U>` via `(U)->T`
 *
 * Another way to think about this is: `map` transforms a type by changing the
 * output types of its API, while `contraMap` transforms a type by changing the
 * *input* types of its API.
 */
public fun <T1, T2> Sink<T1>.contraMap(transform: (T2) -> T1): Sink<T2> = Sink {
  this@contraMap.send(transform(it))
}

/**
 * Collects from a [Flow] by converting each item into a [WorkflowAction] and then sending them
 * to the [actionSink]. This operator propagates back pressure from the workflow runtime, so if there
 * is a lot of contention on the workflow runtime the flow will be suspended while the action is
 * queued.
 *
 * This method is intended to be used from [BaseRenderContext.runningSideEffect].
 *
 * Example:
 * ```
 * context.runningSideEffect("collector") {
 *   myFlow.collectToSink(context.actionSink) { value ->
 *     action("collect") { setOutput(value) }
 *   }
 * }
 * ```
 */
internal suspend fun <T, PropsT, StateT, OutputT> Flow<T>.collectToSink(
  actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>>,
  handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
  collect {
    // Don't process the emission until the last emission has had its action executed by the
    // workflow runtime.
    actionSink.sendAndAwaitApplication(handler(it))
  }
}

/**
 * Sends [action] to this [Sink] and suspends until after [action]'s [WorkflowAction.apply] method
 * has been invoked. Since a [Sink] may be backed by an unbounded buffer, this method can be used
 * to apply back pressure to the caller when there are a lot events being sent to the workflow
 * runtime.
 *
 * If this coroutine is cancelled before the action gets applied, the action will not be applied.
 *
 * This method is intended to be used from [BaseRenderContext.runningSideEffect].
 */
internal suspend fun <
  PropsT,
  StateT,
  OutputT
  > Sink<WorkflowAction<PropsT, StateT, OutputT>>.sendAndAwaitApplication(
  action: WorkflowAction<PropsT, StateT, OutputT>
) {
  suspendCancellableCoroutine<Unit> { continuation ->
    val resumingAction = object : WorkflowAction<PropsT, StateT, OutputT>() {
      override fun toString(): String = "sendAndAwaitApplication(${action})"

      override fun Updater.apply() {
        // Don't execute anything if the caller was cancelled while we were in the queue.
        if (!continuation.isActive) return

        with(action) {
          // Forward our Updater to the real action.
          apply()
        }
        continuation.resume(Unit)
      }
    }
    send(resumingAction)
  }
}
