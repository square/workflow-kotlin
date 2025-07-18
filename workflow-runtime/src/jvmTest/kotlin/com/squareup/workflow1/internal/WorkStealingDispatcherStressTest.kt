package com.squareup.workflow1.internal

import com.squareup.workflow1.awaitUntilDone
import com.squareup.workflow1.calculateSaturatingTestThreadCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Returns the maximum number of threads that can be ran in parallel on the host system, rounded
 * down to the nearest even number, and at least 2.
 */
private val saturatingTestThreadCount = calculateSaturatingTestThreadCount(minThreads = 2)

/**
 * Tests that use multiple threads to hammer on [WorkStealingDispatcher] and verify its thread
 * safety. This test must be in JVM since it needs to create threads. Most tests for this class live
 * in the common [WorkStealingDispatcherTest] suite.
 */
class WorkStealingDispatcherStressTest {

  /**
   * This stress-tests the [WorkStealingDispatcher.dispatch] method only, without ever running any
   * tasks from the queue until all dispatches are done. Only dispatches are done in parallel.
   */
  @Suppress("CheckResult")
  @Test fun stressTestDispatchingFromMultipleThreadsNoExecuting() {
    // Use a test dispatcher so we can pause time.
    val baseDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)
    val scope = CoroutineScope(dispatcher)

    val numDispatchThreads = saturatingTestThreadCount
    val dispatchesPerThread = 100
    // This pair of latches ensures that all threads start their dispatch loops as close to the same
    // exact instant as possible.
    val threadsFinishedLaunching = CountDownLatch(numDispatchThreads)
    val startDispatching = CountDownLatch(1)
    val doneDispatching = CountDownLatch(numDispatchThreads)
    val finishedDispatches = CountDownLatch(numDispatchThreads * dispatchesPerThread)
    repeat(numDispatchThreads) {
      thread(name = "dispatch-$it") {
        threadsFinishedLaunching.countDown()
        startDispatching.awaitUntilDone()

        // Launch a storm of coroutines to hammer the dispatcher.
        repeat(dispatchesPerThread) {
          dispatcher.dispatch(scope.coroutineContext, Runnable {
            finishedDispatches.countDown()
          })
        }
        doneDispatching.countDown()
      }
    }

    threadsFinishedLaunching.awaitUntilDone()
    startDispatching.countDown()
    doneDispatching.awaitUntilDone()
    // Now we have a bunch of stuff queued up, drain it.
    dispatcher.advanceUntilIdle()
    finishedDispatches.awaitUntilDone()

    // Once await() returns normally, its count is at 0 by definition, which means all the
    // dispatches were processed. But assert anyway, just to make it clear.
    assertEquals(0, finishedDispatches.count)
  }

  /**
   * This stress-tests interleaving [WorkStealingDispatcher.dispatch] with
   * [WorkStealingDispatcher.advanceUntilIdle]. Both methods are ran in parallel.
   */
  @Suppress("CheckResult")
  @Test fun stressTestDispatchingFromMultipleThreadsWithAdvanceUntilIdle() {
    // Use a test dispatcher so we can pause time.
    val baseDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)
    val scope = CoroutineScope(dispatcher)

    val numThreads = saturatingTestThreadCount
    val numDispatchThreads = numThreads / 2
    val numAdvanceThreads = numThreads / 2
    val dispatchesPerThread = 100
    // This pair of latches ensures that all threads start their dispatch loops as close to the same
    // exact instant as possible.
    val threadsFinishedLaunching = CountDownLatch(numThreads)
    val startDispatching = CountDownLatch(1)
    val doneDispatching = CountDownLatch(numDispatchThreads)
    val finishedDispatches = CountDownLatch(numDispatchThreads * dispatchesPerThread)
    val statuses = Array(numDispatchThreads * dispatchesPerThread) { AtomicInteger() }

    repeat(numDispatchThreads) { threadNum ->
      thread(name = "dispatch-$threadNum") {
        threadsFinishedLaunching.countDown()
        startDispatching.awaitUntilDone()

        // Launch a storm of coroutines to hammer the dispatcher.
        repeat(dispatchesPerThread) { dispatchNum ->
          dispatcher.dispatch(scope.coroutineContext, Runnable {
            statuses[(threadNum * dispatchesPerThread) + dispatchNum].incrementAndGet()
            finishedDispatches.countDown()
          })
        }
        doneDispatching.countDown()
      }
    }

    repeat(numAdvanceThreads) {
      thread(name = "advance-$it") {
        threadsFinishedLaunching.countDown()
        startDispatching.awaitUntilDone()

        // Launch a storm of coroutines to hammer the dispatcher.
        while (finishedDispatches.count > 0) {
          dispatcher.advanceUntilIdle()
        }
      }
    }

    threadsFinishedLaunching.awaitUntilDone()
    startDispatching.countDown()
    doneDispatching.awaitUntilDone()
    // Now we have a bunch of stuff queued up, drain it.
    dispatcher.advanceUntilIdle()
    finishedDispatches.awaitUntilDone()

    // Once await() returns normally, its count is at 0 by definition, which means all the
    // dispatches were processed. But assert anyway, just to make it clear.
    assertEquals(0, finishedDispatches.count)

    // Ensure that all tasks were ran exactly once.
    assertTrue(statuses.all { it.get() == 1 })
  }

  /**
   * This stress-tests interleaving [WorkStealingDispatcher.dispatch] with
   * [WorkStealingDispatcher.advanceUntilIdle]. Both methods are ran in parallel.
   */
  @Suppress("CheckResult")
  @Test fun stressTestDispatchingFromMultipleThreadsWithDispatch() {
    // Use a test dispatcher so we can pause time.
    val baseDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)
    val scope = CoroutineScope(dispatcher)

    val numThreads = saturatingTestThreadCount
    val numDispatchThreads = numThreads / 2
    val numAdvanceThreads = numThreads / 2
    val dispatchesPerThread = 100
    // This pair of latches ensures that all threads start their dispatch loops as close to the same
    // exact instant as possible.
    val threadsFinishedLaunching = CountDownLatch(numThreads)
    val startDispatching = CountDownLatch(1)
    val doneDispatching = CountDownLatch(numDispatchThreads)
    val finishedDispatches = CountDownLatch(numDispatchThreads * dispatchesPerThread)
    val statuses = Array(numDispatchThreads * dispatchesPerThread) { AtomicInteger() }

    repeat(numDispatchThreads) { threadNum ->
      thread(name = "dispatch-$threadNum") {
        threadsFinishedLaunching.countDown()
        startDispatching.awaitUntilDone()

        // Launch a storm of coroutines to hammer the dispatcher.
        repeat(dispatchesPerThread) { dispatchNum ->
          dispatcher.dispatch(scope.coroutineContext, Runnable {
            statuses[(threadNum * dispatchesPerThread) + dispatchNum].incrementAndGet()
            finishedDispatches.countDown()
          })
        }
        doneDispatching.countDown()
      }
    }

    repeat(numAdvanceThreads) {
      thread(name = "advance-$it") {
        threadsFinishedLaunching.countDown()
        startDispatching.awaitUntilDone()

        // Launch a storm of coroutines to hammer the dispatcher.
        while (finishedDispatches.count > 0) {
          baseDispatcher.scheduler.advanceUntilIdle()
        }
      }
    }

    threadsFinishedLaunching.awaitUntilDone()
    startDispatching.countDown()
    doneDispatching.awaitUntilDone()
    // Now we have a bunch of stuff queued up, drain it.
    dispatcher.advanceUntilIdle()
    finishedDispatches.awaitUntilDone()

    // Once await() returns normally, its count is at 0 by definition, which means all the
    // dispatches were processed. But assert anyway, just to make it clear.
    assertEquals(0, finishedDispatches.count)

    // Ensure that all tasks were ran exactly once.
    assertTrue(statuses.all { it.get() == 1 })
  }

  /**
   * This stress-tests interleaving [WorkStealingDispatcher.dispatch] with
   * [WorkStealingDispatcher.advanceUntilIdle]. Both methods are ran in parallel.
   */
  @Suppress("CheckResult")
  @Test fun stressTestDispatchingFromMultipleThreadsWithUnconfined() {
    // Use a test dispatcher so we can pause time.
    val dispatcher = WorkStealingDispatcher(Dispatchers.Unconfined)
    val scope = CoroutineScope(dispatcher)

    val numDispatchThreads = saturatingTestThreadCount
    val dispatchesPerThread = 100
    // This pair of latches ensures that all threads start their dispatch loops as close to the same
    // exact instant as possible.
    val threadsFinishedLaunching = CountDownLatch(numDispatchThreads)
    val startDispatching = CountDownLatch(1)
    val doneDispatching = CountDownLatch(numDispatchThreads)
    val finishedDispatches = CountDownLatch(numDispatchThreads * dispatchesPerThread)
    val statuses = Array(numDispatchThreads * dispatchesPerThread) { AtomicInteger() }

    repeat(numDispatchThreads) { threadNum ->
      thread(name = "dispatch-$threadNum") {
        threadsFinishedLaunching.countDown()
        startDispatching.awaitUntilDone()

        // Launch a storm of coroutines to hammer the dispatcher.
        repeat(dispatchesPerThread) { dispatchNum ->
          dispatcher.dispatch(scope.coroutineContext, Runnable {
            statuses[(threadNum * dispatchesPerThread) + dispatchNum].incrementAndGet()
            finishedDispatches.countDown()
          })
        }
        doneDispatching.countDown()
      }
    }

    threadsFinishedLaunching.awaitUntilDone()
    startDispatching.countDown()
    doneDispatching.awaitUntilDone()
    // Now we have a bunch of stuff queued up, drain it.
    dispatcher.advanceUntilIdle()
    finishedDispatches.awaitUntilDone()

    // Once await() returns normally, its count is at 0 by definition, which means all the
    // dispatches were processed. But assert anyway, just to make it clear.
    assertEquals(0, finishedDispatches.count)

    // Ensure that all tasks were ran exactly once.
    assertTrue(statuses.all { it.get() == 1 })
  }
}
