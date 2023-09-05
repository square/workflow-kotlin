@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * [Worker] that performs some action when the worker is started and/or stopped.
 *
 * A [Worker] is stopped when its parent [Workflow] finishes a render pass without running the
 * worker, or when the parent workflow is itself torn down.
 *
 * Note that there is currently an [issue](https://github.com/square/workflow-kotlin/issues/1093)
 * which can effect whether a [LifecycleWorker] is ever executed.
 * See more details at [BaseRenderContext.runningSideEffect].
 */
public abstract class LifecycleWorker : Worker<Nothing> {

  /**
   * Called when this worker is started. It is executed concurrently with the parent workflow –
   * the first render pass that starts this worker *will not* wait for this method to return, and
   * one or more additional render passes may occur before this method is called.
   * This behavior may change to be more strict in the future.
   *
   * This method will be called exactly once for each matching call to [onStopped], and it will
   * always be called first.
   *
   * Invoked on the dispatcher running the workflow.
   */
  public open fun onStarted() {}

  /**
   * Called when this worker has been torn down. It is executed concurrently with the parent
   * workflow – the render pass that cancels (stops) this worker *will not* wait for this method to
   * return, and one or more additional render passes may occur before this method is called.
   * This behavior may change to be more strict in the future.
   *
   * This method will be called exactly once for each matching call to [onStarted], and it will
   * always be called second.
   *
   * Invoked on the dispatcher running the workflow.
   */
  public open fun onStopped() {}

  final override fun run(): Flow<Nothing> = flow {
    onStarted()

    try {
      // Hang forever, or until this coroutine is cancelled.
      // Don't use CancellableContinuation.invokeOnCancellation because it doesn't have any
      // guarantees about which thread it's run on. Using try/finally means the cancellation action
      // doesn't block the cancellation, but ensures it's run on the correct dispatcher.
      suspendCancellableCoroutine<Nothing> { }
    } finally {
      onStopped()
    }
  }

  /**
   * Equates [LifecycleWorker]s that have the same concrete class.
   */
  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean {
    return otherWorker::class == this::class
  }
}
