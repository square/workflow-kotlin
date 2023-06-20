@file:OptIn(ExperimentalCoroutinesApi::class)

package com.squareup.workflow1.testing

import app.cash.turbine.Event.Item
import app.cash.turbine.test
import com.squareup.workflow1.Worker
import com.squareup.workflow1.testing.WorkflowTestRuntime.Companion.DEFAULT_TIMEOUT_MS
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

public interface WorkerTester<T> {

  /**
   * Access the [TestCoroutineScheduler] of the [kotlinx.coroutines.test.TestScope] running
   * the [Worker]'s [test].
   *
   * This can be used to advance virtual time for the
   * [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher]
   * that the Worker's flow is flowing on.
   */
  public val testCoroutineScheduler: TestCoroutineScheduler

  /**
   * Suspends until the worker emits its next value, then returns it.
   *
   * Throws an [AssertionError] if the Worker completes or has an error.
   */
  public suspend fun nextOutput(): T

  /**
   * Throws an [AssertionError] if an output has been emitted since the last call to [nextOutput].
   */
  public fun assertNoOutput()

  /**
   * Suspends until the worker emits an output or finishes.
   *
   * Throws an [AssertionError] if an output was emitted or the Worker has an error.
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
@OptIn(DelicateCoroutinesApi::class)
public fun <T> Worker<T>.test(
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
  block: suspend WorkerTester<T>.() -> Unit
) {
  runTest {
    // Use a Turbine which consumes outputs/errors from an underlying channel.
    run().test(
      timeout = timeoutMs.toDuration(MILLISECONDS)
    ) {
      val tester = object : WorkerTester<T> {
        override val testCoroutineScheduler: TestCoroutineScheduler = testScheduler

        override suspend fun nextOutput(): T = awaitItem()

        override fun assertNoOutput() {
          try {
            expectNoEvents()
          } catch (e: AssertionError) {
            throw AssertionError("Expected no output to have been emitted.")
          }
        }

        override suspend fun assertFinished() {
          try {
            withTimeoutOrNull(timeoutMs) {
              awaitComplete()
            } ?: throw AssertionError()
          } catch (e: AssertionError) {
            // Note there is some complicated logic here to build the message. The messages predate
            // Turbine integration but we wanted to keep them stable, and so extract what's needed
            // from the Turbine AssertionErrors.
            val message = buildString {
              append("Expected Worker to be finished.")
              val outputStrings = cancelAndConsumeRemainingEvents().filterIsInstance<Item<T>>()
                .map { it.value.toString() }.toMutableList()
              // Consumed and only reported in the exception.
              e.message?.substringAfter(
                delimiter = "Item(",
                missingDelimiterValue = ""
              )?.substringBeforeLast(
                delimiter = ')',
                missingDelimiterValue = ""
              )?.let {
                if (it.isNotEmpty()) {
                  outputStrings.add(0, it)
                }
              }
              if (outputStrings.isNotEmpty()) {
                append(" Emitted outputs: $outputStrings")
              }
            }
            throw AssertionError(message)
          }
        }

        override fun assertNotFinished() {
          if (asChannel().isClosedForReceive) {
            throw AssertionError("Expected Worker to not be finished.")
          }
        }

        override suspend fun getException(): Throwable = try {
          awaitError()
        } catch (e: Throwable) {
          e
        }

        override suspend fun cancelWorker() {
          cancelAndIgnoreRemainingEvents()
        }
      }

      tester.block()
      cancelAndIgnoreRemainingEvents()
    }
  }
}
