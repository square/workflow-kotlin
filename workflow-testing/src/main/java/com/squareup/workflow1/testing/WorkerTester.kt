@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.squareup.workflow1.testing

import com.squareup.workflow1.Worker
import com.squareup.workflow1.testing.WorkflowTestRuntime.Companion.DEFAULT_TIMEOUT_MS
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

public interface WorkerTester<T> {

  /**
   * Suspends until the worker emits its next value, then returns it.
   */
  public suspend fun nextOutput(): T

  /**
   * Throws an [AssertionError] if an output has been emitted since the last call to [nextOutput].
   */
  public fun assertNoOutput()

  /**
   * Suspends until the worker emits an output or finishes.
   *
   * Throws an [AssertionError] if an output was emitted.
   */
  public suspend fun assertFinished()

  /**
   * Throws an [AssertionError] immediately if the worker is finished.
   */
  public fun assertNotFinished()

  /**
   * Suspends until the worker throws an exception, then returns it.
   */
  public suspend fun getException(): Throwable

  /**
   * Cancels the worker and suspends until it's finished cancelling (joined).
   */
  public suspend fun cancelWorker()
}

/**
 * Test a [Worker] by defining assertions on its output within [block].
 */
public fun <T> Worker<T>.test(
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
  block: suspend WorkerTester<T>.() -> Unit
) {
  runBlocking {
    supervisorScope {
      val channel: ReceiveChannel<T> = run().produceIn(this + Unconfined)

      val tester = object : WorkerTester<T> {
        override suspend fun nextOutput(): T = channel.receive()

        override fun assertNoOutput() {
          // isEmpty returns false if the channel is closed.
          if (!channel.isEmpty && !channel.isClosedForReceive) {
            throw AssertionError("Expected no output to have been emitted.")
          }
        }

        override suspend fun assertFinished() {
          if (!channel.isClosedForReceive) {
            val message = buildString {
              append("Expected Worker to be finished.")
              val outputs = mutableListOf<T>()
              while (!channel.isEmpty) {
                @Suppress("UNCHECKED_CAST")
                outputs += channel.tryReceive().getOrNull() as T
              }
              if (outputs.isNotEmpty()) {
                append(" Emitted outputs: $outputs")
              }
            }
            throw AssertionError(message)
          }
        }

        override fun assertNotFinished() {
          if (channel.isClosedForReceive) {
            throw AssertionError("Expected Worker to not be finished.")
          }
        }

        override suspend fun getException(): Throwable = try {
          val output = channel.receive()
          throw AssertionError("Expected Worker to throw an exception, but emitted output: $output")
        } catch (e: Throwable) {
          e
        }

        override suspend fun cancelWorker() {
          channel.cancel()
        }
      }

      // Yield to let the produce coroutine start, since we can't specify UNDISPATCHED.
      yield()

      withTimeout(timeoutMs) {
        block(tester)
      }

      coroutineContext.cancelChildren()
    }
  }
}
