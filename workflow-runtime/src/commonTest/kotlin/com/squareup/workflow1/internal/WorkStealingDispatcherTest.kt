package com.squareup.workflow1.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Most of the tests for [WorkStealingDispatcher] are here. They cover specific code paths.
 * `WorkStealingDispatcherStressTest` in the JVM source set has multithreading stress tests.
 */
class WorkStealingDispatcherTest {

  // region Unit tests calling WorkStealingDispatcher methods directly

  @OptIn(InternalCoroutinesApi::class)
  @Test
  fun supportsDelay_whenDelegateDoes() {
    val dispatcher = WorkStealingDispatcher(StandardTestDispatcher())
    assertTrue(dispatcher is Delay)
  }

  @OptIn(InternalCoroutinesApi::class)
  @Test
  fun doesNotSupportDelay_whenDelegateDoesNot() {
    val dispatcher = WorkStealingDispatcher(NoopContinuationInterceptor())
    assertFalse(dispatcher is Delay)
  }

  @Test fun wrapDispatcherFrom_worksWhenEmpty() = runTest {
    // Since this uses the Default dispatcher, we can't rely on any ordering guarantees.
    val dispatcher = WorkStealingDispatcher.wrapDispatcherFrom(EmptyCoroutineContext)
    var wasDispatched = false

    val job = launch(dispatcher) {
      wasDispatched = true
    }

    job.join()
    assertTrue(wasDispatched)
  }

  @Test fun wrapDispatcherFrom_worksWhenInterceptorNotDispatcher() = runTest {
    val dispatcher = WorkStealingDispatcher.wrapDispatcherFrom(NoopContinuationInterceptor())

    expect(0)
    launch(dispatcher) {
      expect(1)
    }
    expect(2)
  }

  @Test fun wrapDispatcherFrom_takesDispatcherFromContext() = runTest {
    val dispatcher = WorkStealingDispatcher.wrapDispatcherFrom(currentCoroutineContext())

    expect(0)
    launch(dispatcher) {
      expect(2)
    }
    expect(1)

    testScheduler.advanceUntilIdle()
    expect(3)
  }

  @Test fun wrapDispatcherFrom_wrapsAnotherWorkStealingDispatcher() {
    val base = StandardTestDispatcher()
    val intermediate = WorkStealingDispatcher.wrapDispatcherFrom(base)
    val final = WorkStealingDispatcher.wrapDispatcherFrom(intermediate)

    assertNotSame(intermediate, final)
  }

  @Test fun dispatch_runsImmediatelyWhenDelegateIsUnconfined() {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Unconfined)

    expect(0)
    dispatcher.dispatch {
      expect(1)
    }
    expect(2)
  }

  @Test fun dispatchNested_enqueuesWhenDelegateIsUnconfined() {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Unconfined)

    expect(0)
    dispatcher.dispatch {
      expect(1)

      // This dispatch should get enqueued to Unconfined's threadlocal queue.
      dispatcher.dispatch {
        expect(3)
      }

      expect(2)
    }
    expect(4)
  }

  @Test fun dispatch_queues_whenDelegateNeedsDispatch() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
    }
    expect(1)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(3)
  }

  @Test fun dispatch_runsMultipleTasksInOrder_whenDelegateNeedsDispatch() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(3)
    }
    expect(1)
    dispatcher.dispatch {
      expect(4)
    }
    expect(2)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(5)
  }

  @Test fun dispatchNested_runsInOrder_whenDelegateNeedsDispatch() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
      dispatcher.dispatch {
        expect(5)
      }

      expect(3)

      dispatcher.dispatch {
        expect(6)
      }
      expect(4)
    }
    expect(1)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(7)
  }

  @Test fun nestedDispatcher_outerAdvanceAdvancesInner() {
    val base = StandardTestDispatcher()
    val outerDispatcher = WorkStealingDispatcher(base)
    val innerDispatcher = WorkStealingDispatcher(outerDispatcher)

    expect(0)
    outerDispatcher.dispatch {
      expect(2)
    }
    innerDispatcher.dispatch {
      expect(3)
    }
    expect(1)

    outerDispatcher.advanceUntilIdle()
    expect(4)
  }

  @Test fun nestedDispatcher_innerDoesNotAdvanceAdvanceOuter() {
    val base = StandardTestDispatcher()
    val outerDispatcher = WorkStealingDispatcher(base)
    val innerDispatcher = WorkStealingDispatcher(outerDispatcher)

    expect(0)
    outerDispatcher.dispatch {
      expect(4)
    }
    innerDispatcher.dispatch {
      expect(2)
    }
    expect(1)

    innerDispatcher.advanceUntilIdle()
    expect(3)

    outerDispatcher.advanceUntilIdle()
    expect(5)
  }

  @Test fun dispatch_interceptsAndResumesContinuation() {
    val baseDispatcher = object : ContinuationInterceptor,
      AbstractCoroutineContextElement(ContinuationInterceptor) {
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expect(1)
        // Needs to return a different instance.
        return object : Continuation<T> by continuation {
          override fun resumeWith(result: Result<T>) {
            expect(2)
            continuation.resumeWith(result)
            expect(4)
          }
        }
      }
    }
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(3)
    }
    expect(5)
  }

  @Test fun dispatch_interceptsAndReleasesContinuationWhenIntercepted() {
    var intercepted: Continuation<*>? = null
    val baseDispatcher = object : ContinuationInterceptor,
      AbstractCoroutineContextElement(ContinuationInterceptor) {
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expect(1)
        // Needs to return a different instance.
        return object : Continuation<T> by continuation {}.also { intercepted = it }
      }

      override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        // Continuation should be released before it runs its own tasks.
        expect(2)
        assertSame(intercepted, continuation)
      }
    }
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(3)
    }
    expect(4)
  }

  @Test fun dispatch_interceptsAndReleasesContinuationWhenAdvanced() {
    var intercepted: Continuation<*>? = null

    val baseDispatcher = object : ContinuationInterceptor,
      AbstractCoroutineContextElement(ContinuationInterceptor) {
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expect(1)

        return object : Continuation<T> by continuation {
          override fun resumeWith(result: Result<T>) {
            // "Suspend" forever, never "dispatch".
          }
        }.also { intercepted = it }
      }

      override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        // Continuation should be released before it runs its own tasks.
        expect(3)
        assertSame(intercepted, continuation)
      }
    }
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(4)
    }
    expect(2)

    dispatcher.advanceUntilIdle()
    expect(5)
  }

  @Test fun dispatch_doesNotReleaseContinuationWhenNotIntercepted() {
    val baseDispatcher = object : ContinuationInterceptor,
      AbstractCoroutineContextElement(ContinuationInterceptor) {
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expect(1)
        return continuation
      }

      override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        fail()
      }
    }
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
    }
    expect(3)
  }

  @Test fun advanceUntilIdle_drainsQueueWhileWaitingForDispatch() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
    }
    dispatcher.dispatch {
      expect(3)
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(4)
  }

  @Test fun advanceUntilIdle_handlesNestedDispatches() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
      dispatcher.dispatch {
        expect(4)
      }
      expect(3)
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(5)
  }

  @Test fun advanceUntilIdle_canBeCalledReentrantly() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
      dispatcher.dispatch {
        expect(4)
      }
      expect(3)

      dispatcher.advanceUntilIdle()

      expect(5)
      dispatcher.dispatch {
        expect(7)
      }
      expect(6)
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(8)
  }

  /**
   * This test validates an extreme case of reentrant [WorkStealingDispatcher.advanceUntilIdle]
   * calls, where every dispatched tasks itself advances the queue. The order in which queued tasks
   * are started should be the same as if the queue were only advanced by a single call.
   */
  @Test fun advanceUntilIdle_isEager_whenCalledReentrantlyWhileMultipleTasksQueued() {
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)

    // Task 1
    dispatcher.dispatch {
      expect(3)
      // Task 1.1
      dispatcher.dispatch {
        expect(7)

        // Advance 4: Advance 3 is still running Task 2, so this will see Task 2 and run it.
        dispatcher.advanceUntilIdle()
        expect(10)
      }
      expect(4)

      // Advance 2: Since the Advance 1 is still processing Task 1, this call will see Task 2 and
      // run it.
      dispatcher.advanceUntilIdle()
      expect(12)
    }

    expect(1)

    // Task 2
    dispatcher.dispatch {
      expect(5)
      // Task 2.1
      dispatcher.dispatch {
        expect(8)

        // Advance 5: There are no more queued tasks, so this is a noop.
        dispatcher.advanceUntilIdle()
        expect(9)
      }
      expect(6)

      // Advance 3: Since Advance 2 is still busy running Task 1, this call will see Task 1.1 and
      // run it.
      dispatcher.advanceUntilIdle()
      expect(11)
    }

    // Advance 1: This call kicks everything off by running Task 1.
    expect(2)
    dispatcher.advanceUntilIdle()
    expect(13)
  }

  @Test fun advanceUntilIdle_noopsWhenNoTasks() {
    val dispatcher = WorkStealingDispatcher(StandardTestDispatcher())

    // Just make sure this doesn't throw when the queue is empty.
    dispatcher.advanceUntilIdle()
  }

  @Test fun doesNotDoubleDispatch_whenDispatchedAfterAdvance() {
    val baseDispatcher = RecordingDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(3)

    baseDispatcher.blocks.single().run()
    expect(4)
  }

  @Test fun doesNotDoubleDispatch_whenAdvancedAfterDispatch() {
    val baseDispatcher = RecordingDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    dispatcher.dispatch {
      expect(2)
    }
    expect(1)

    baseDispatcher.blocks.single().run()
    expect(3)

    dispatcher.advanceUntilIdle()
    expect(4)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun limitedParallelism_unsupported_whenDelegateNotDispatcher() {
    val dispatcher = WorkStealingDispatcher(NoopContinuationInterceptor())

    assertFailsWith<UnsupportedOperationException> {
      dispatcher.limitedParallelism(2)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun limitedParallelism_limitsParallelism() {
    val baseDispatcher = RecordingDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)
    val limited = dispatcher.limitedParallelism(2)

    // This particular ordering in which tasks are executed is an implementation detail of the
    // default implementation of LimitedParallelism, so we can't use expect and don't care about
    // the ordering anyway, just how many are executed at each step below.
    var tasksRan = 0
    repeat(3) {
      limited.dispatch {
        tasksRan++
      }
    }
    assertEquals(0, tasksRan)
    assertEquals(2, baseDispatcher.blocks.size)

    baseDispatcher.blocks.removeFirst().run()
    assertEquals(2, tasksRan)
    assertEquals(1, baseDispatcher.blocks.size)

    baseDispatcher.blocks.removeFirst().run()
    assertEquals(3, tasksRan)
    assertEquals(0, baseDispatcher.blocks.size)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun limitedParallelism_isAlsoWorkStealing() {
    val baseDispatcher = RecordingDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)
    val limited = dispatcher.limitedParallelism(2)

    expect(0)
    limited.dispatch {
      expect(2)
    }
    limited.dispatch {
      expect(3)
    }
    limited.dispatch {
      expect(4)
    }
    expect(1)

    assertEquals(2, baseDispatcher.blocks.size)
    dispatcher.advanceUntilIdle()
    expect(5)
    assertEquals(2, baseDispatcher.blocks.size)
  }

  @OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
  @Test
  fun limitedParallelism_preservesDelayability() {
    val baseDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)
    val limited = dispatcher.limitedParallelism(2)

    assertTrue(limited is Delay)
  }

  /**
   * This tests that the delegate ordering is respected, which also implies that it preserves
   * "parallelizability" of the delegate dispatcher – if it would run tasks in parallel, then so
   * would [WorkStealingDispatcher].
   */
  @Test fun preservesDelegateDispatchOrdering() {
    val baseDispatcher = RecordingDispatcher()
    val dispatcher = WorkStealingDispatcher(baseDispatcher)

    expect(0)
    // We're going to run these in reverse order, so count down instead of up.
    dispatcher.dispatch {
      expect(4)
    }
    dispatcher.dispatch {
      expect(3)
    }
    dispatcher.dispatch {
      expect(2)
    }
    expect(1)

    assertEquals(3, baseDispatcher.blocks.size)
    baseDispatcher.blocks.asReversed().forEach {
      it.run()
    }
    expect(5)
  }

  // endregion
  // region Integration tests with higher-level coroutine APIs

  @Test fun integration_unconfined() = runTest {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Unconfined)

    expect(0)
    launch(dispatcher) {
      expect(1)
    }
    expect(2)
  }

  @Test fun integration_confined_whenAdvanced() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    launch(dispatcher) {
      expect(2)
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(3)
  }

  @Test fun integration_confined_whenDispatched() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    expect(0)
    launch(dispatcher) {
      expect(2)
    }
    expect(1)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(3)
  }

  @Test fun integration_yield_whenAdvanced() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    launch(dispatcher) {
      expect(0)
      yield()
      expect(2)
    }
    launch(dispatcher) {
      expect(1)
      yield()
      expect(3)
    }

    dispatcher.advanceUntilIdle()
    expect(4)
  }

  @Test fun integration_yield_whenDispatched() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    launch(dispatcher) {
      expect(0)
      yield()
      expect(2)
    }
    launch(dispatcher) {
      expect(1)
      yield()
      expect(3)
    }

    testDispatcher.scheduler.advanceUntilIdle()
    expect(4)
  }

  @Test fun integration_delay_whenAdvanced() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    launch(dispatcher) {
      expect(0)
      delay(20)
      expect(4)
    }
    launch(dispatcher) {
      expect(1)
      delay(10)
      expect(3)
    }

    dispatcher.advanceUntilIdle()
    expect(2)

    testScheduler.advanceUntilIdle()
    expect(5)
  }

  @Test fun integration_delay_whenDispatched() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    launch(dispatcher) {
      expect(0)
      delay(20)
      expect(3)
    }
    launch(dispatcher) {
      expect(1)
      delay(10)
      expect(2)
    }

    testDispatcher.scheduler.advanceUntilIdle()
    expect(4)
  }

  @Test fun integration_error_noFinally_whenAdvanced() {
    val exceptions = mutableListOf<Throwable>()
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)
    val scope = CoroutineScope(
      dispatcher + CoroutineExceptionHandler { _, throwable ->
        expect(3)
        exceptions += throwable
      }
    )

    expect(0)
    val job = scope.launch {
      expect(2)
      throw ExpectedException()
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(4)

    assertTrue(job.isCancelled)
    assertTrue(exceptions.single() is ExpectedException)
  }

  @Test fun integration_error_noFinally_whenDispatched() {
    val exceptions = mutableListOf<Throwable>()
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)
    val scope = CoroutineScope(
      dispatcher + CoroutineExceptionHandler { _, throwable ->
        expect(3)
        exceptions += throwable
      }
    )

    expect(0)
    val job = scope.launch {
      expect(2)
      throw ExpectedException()
    }
    expect(1)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(4)

    assertTrue(job.isCancelled)
    assertTrue(exceptions.single() is ExpectedException)
  }

  @Test fun integration_error_withCatch_whenAdvanced() {
    val exceptions = mutableListOf<Throwable>()
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)
    val scope = CoroutineScope(
      dispatcher + CoroutineExceptionHandler { _, throwable ->
        exceptions += throwable
      }
    )

    expect(0)
    val job = scope.launch {
      expect(2)
      try {
        throw ExpectedException()
      } catch (e: ExpectedException) {
        expect(3)
      }
      expect(4)
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(5)

    assertFalse(job.isCancelled)
    assertTrue(exceptions.isEmpty())
  }

  @Test fun integration_error_withCatch_whenDispatched() {
    val exceptions = mutableListOf<Throwable>()
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)
    val scope = CoroutineScope(
      dispatcher + CoroutineExceptionHandler { _, throwable ->
        exceptions += throwable
      }
    )

    expect(0)
    val job = scope.launch {
      expect(2)
      try {
        throw ExpectedException()
      } catch (e: ExpectedException) {
        expect(3)
      }
      expect(4)
    }
    expect(1)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(5)

    assertFalse(job.isCancelled)
    assertTrue(exceptions.isEmpty())
  }

  @Test fun integration_error_withFinally_whenAdvanced() {
    val exceptions = mutableListOf<Throwable>()
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)
    val scope = CoroutineScope(
      dispatcher + CoroutineExceptionHandler { _, throwable ->
        exceptions += throwable
      }
    )

    expect(0)
    val job = scope.launch {
      expect(2)
      try {
        throw ExpectedException()
      } finally {
        expect(3)
      }
    }
    expect(1)

    dispatcher.advanceUntilIdle()
    expect(4)

    assertTrue(job.isCancelled)
    assertTrue(exceptions.single() is ExpectedException)
  }

  @Test fun integration_error_withFinally_whenDispatched() {
    val exceptions = mutableListOf<Throwable>()
    val testDispatcher = StandardTestDispatcher()
    val dispatcher = WorkStealingDispatcher(testDispatcher)
    val scope = CoroutineScope(
      dispatcher + CoroutineExceptionHandler { _, throwable ->
        exceptions += throwable
      }
    )

    expect(0)
    val job = scope.launch {
      expect(2)
      try {
        throw ExpectedException()
      } finally {
        expect(3)
      }
    }
    expect(1)

    testDispatcher.scheduler.advanceUntilIdle()
    expect(4)

    assertTrue(job.isCancelled)
    assertTrue(exceptions.single() is ExpectedException)
  }

  /**
   * This tests a specific case mentioned by the docs on `dispatch` as an example why not to invoke
   * the runnable in-place.
   */
  @Test fun integration_yieldInLoop_whenAdvanced() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    launch(dispatcher) {
      // Big loop to try to trigger stack overflow.
      repeat(9_999) {
        yield()
      }
    }

    dispatcher.advanceUntilIdle()
  }

  /**
   * This tests a specific case mentioned by the docs on `dispatch` as an example why not to invoke
   * the runnable in-place.
   */
  @Test fun integration_yieldInLoop_whenDispatched() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val dispatcher = WorkStealingDispatcher(testDispatcher)

    launch(dispatcher) {
      // Big loop to try to trigger stack overflow.
      repeat(9_999) {
        yield()
      }
    }

    testDispatcher.scheduler.advanceUntilIdle()
  }

  // endregion
  // region Test helpers

  private fun CoroutineDispatcher.dispatch(block: () -> Unit) {
    dispatch(this, Runnable { block() })
  }

  private val expectLock = Lock()
  private var current = 0
  private fun expect(expected: Int) {
    expectLock.withLock {
      assertEquals(expected, current, "Expected to be at step $expected but was at $current")
      current++
    }
  }

  private class NoopContinuationInterceptor : ContinuationInterceptor,
    AbstractCoroutineContextElement(ContinuationInterceptor) {

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
      object : Continuation<T> by continuation {}
  }

  private class RecordingDispatcher : CoroutineDispatcher() {
    val blocks = ArrayDeque<Runnable>()

    override fun dispatch(
      context: CoroutineContext,
      block: Runnable
    ) {
      blocks += block
    }
  }

  private class ExpectedException : RuntimeException()

  // endregion
}
