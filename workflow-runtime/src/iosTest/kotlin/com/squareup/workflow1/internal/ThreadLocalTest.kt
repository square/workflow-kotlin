package com.squareup.workflow1.internal

import platform.Foundation.NSCondition
import platform.Foundation.NSThread
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadLocalTest {

  @Volatile
  private var valueFromThread: Int = -1

  @Test fun initialValue() {
    val threadLocal = ThreadLocal(initialValue = { 42 })
    assertEquals(42, threadLocal.get())
  }

  @Test fun settingValue() {
    val threadLocal = ThreadLocal(initialValue = { 42 })
    threadLocal.set(0)
    assertEquals(0, threadLocal.get())
  }

  @Test fun initialValue_inSeparateThread_afterChanging() {
    val threadLocal = ThreadLocal(initialValue = { 42 })
    threadLocal.set(0)

    val thread = NSThread {
      valueFromThread = threadLocal.get()
    }
    thread.start()
    thread.join()

    assertEquals(42, valueFromThread)
  }

  @Test fun set_fromDifferentThreads_doNotConflict() {
    val threadLocal = ThreadLocal(initialValue = { 0 })
    // threadStartedLatch and firstReadLatch together form a barrier: the allow the background
    // to start up and get to the same point as the test thread, just before writing to the
    // ThreadLocal, before allowing both threads to perform the write as close to the same time as
    // possible.
    val threadStartedLatch = NSCondition()
    val firstReadLatch = NSCondition()
    val firstReadDoneLatch = NSCondition()
    val secondReadLatch = NSCondition()

    val thread = NSThread {
      // Wait on the barrier to sync with the test thread.
      threadStartedLatch.signal()
      firstReadLatch.wait()
      threadLocal.set(1)

      // Ensure we can see our read immediately, then wait for the test thread to verify. This races
      // with the set(2) in the test thread, but that's fine. We'll double-check the value later.
      valueFromThread = threadLocal.get()
      firstReadDoneLatch.signal()
      secondReadLatch.wait()

      // Read one last time since now the test thread's second write is done.
      valueFromThread = threadLocal.get()
    }
    thread.start()

    // Wait for the other thread to start, then both threads set the value to something different
    // at the same time.
    threadStartedLatch.wait()
    firstReadLatch.signal()
    threadLocal.set(2)

    // Wait for the background thread to finish setting value, then ensure that both threads see
    // independent values.
    firstReadDoneLatch.wait()
    assertEquals(1, valueFromThread)
    assertEquals(2, threadLocal.get())

    // Change the value in this thread then read it again from the background thread.
    threadLocal.set(3)
    secondReadLatch.signal()
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
}
