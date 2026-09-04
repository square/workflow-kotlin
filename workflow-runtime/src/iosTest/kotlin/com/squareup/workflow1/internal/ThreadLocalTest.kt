package com.squareup.workflow1.internal

import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import platform.Foundation.NSCondition
import platform.Foundation.NSThread

class ThreadLocalTest {

  @Volatile private var valueFromThread: Int = -1

  @Test
  fun initialValue() {
    val threadLocal = ThreadLocal(initialValue = { 42 })
    assertEquals(42, threadLocal.get())
  }

  @Test
  fun settingValue() {
    val threadLocal = ThreadLocal(initialValue = { 42 })
    threadLocal.set(0)
    assertEquals(0, threadLocal.get())
  }

  @Test
  fun nullInitialValue() {
    val threadLocal = ThreadLocal<Int?>(initialValue = { null })
    assertNull(threadLocal.get())
  }

  @Test
  fun settingNull_overridesInitialValue() {
    val threadLocal = ThreadLocal<Int?>(initialValue = { 42 })
    threadLocal.set(null)
    assertNull(threadLocal.get())
  }

  @Test
  fun settingNull_thenNonNull() {
    val threadLocal = ThreadLocal<Int?>(initialValue = { null })
    threadLocal.set(null)
    threadLocal.set(1)
    assertEquals(1, threadLocal.get())
  }

  @Test
  fun initialValue_inSeparateThread_afterChanging() {
    val threadLocal = ThreadLocal(initialValue = { 42 })
    threadLocal.set(0)

    val thread = NSThread { valueFromThread = threadLocal.get() }
    thread.start()
    thread.join()

    assertEquals(42, valueFromThread)
  }

  @Test
  fun set_fromDifferentThreads_doNotConflict() {
    val threadLocal = ThreadLocal(initialValue = { 0 })
    // threadStartedLatch and firstReadLatch together form a barrier: they allow the background
    // thread to start up and get to the same point as the test thread, just before writing to the
    // ThreadLocal, before allowing both threads to perform the write as close to the same time as
    // possible.
    val threadStartedLatch = Latch()
    val firstReadLatch = Latch()
    val firstReadDoneLatch = Latch()
    val secondReadLatch = Latch()

    val thread = NSThread {
      // Wait on the barrier to sync with the test thread.
      threadStartedLatch.open()
      firstReadLatch.await()
      threadLocal.set(1)

      // Ensure we can see our read immediately, then wait for the test thread to verify. This races
      // with the set(2) in the test thread, but that's fine. We'll double-check the value later.
      valueFromThread = threadLocal.get()
      firstReadDoneLatch.open()
      secondReadLatch.await()

      // Read one last time since now the test thread's second write is done.
      valueFromThread = threadLocal.get()
    }
    thread.start()

    // Wait for the other thread to start, then both threads set the value to something different
    // at the same time.
    threadStartedLatch.await()
    firstReadLatch.open()
    threadLocal.set(2)

    // Wait for the background thread to finish setting value, then ensure that both threads see
    // independent values.
    firstReadDoneLatch.await()
    assertEquals(1, valueFromThread)
    assertEquals(2, threadLocal.get())

    // Change the value in this thread then read it again from the background thread.
    threadLocal.set(3)
    secondReadLatch.open()
    thread.join()
    assertEquals(1, valueFromThread)
  }

  private fun NSThread.join() {
    while (!isFinished()) {
      // Avoid being optimized out.
      // Time interval is in seconds.
      NSThread.sleepForTimeInterval(1.0 / 1000)
    }
  }

  /**
   * One-shot latch built on [NSCondition]. A bare `NSCondition.signal()` is lost if nobody is
   * waiting yet, and `wait()` must be called with the lock held, so using the condition directly as
   * a latch deadlocks whenever the opener gets there first (which happens regularly on CI runners).
   * Guarding a flag with the lock makes [open] durable and [await] correct.
   */
  private class Latch {
    private val condition = NSCondition()
    private var isOpen = false

    fun open() {
      condition.lock()
      isOpen = true
      condition.broadcast()
      condition.unlock()
    }

    fun await() {
      condition.lock()
      while (!isOpen) condition.wait()
      condition.unlock()
    }
  }
}
