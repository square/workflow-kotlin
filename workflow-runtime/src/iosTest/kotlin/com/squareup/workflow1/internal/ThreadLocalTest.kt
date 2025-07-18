package com.squareup.workflow1.internal

import platform.Foundation.NSLock
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
    val threadStartedLatch = NSLock().apply { lock() }
    val firstReadLatch = NSLock().apply { lock() }
    val firstReadDoneLatch = NSLock().apply { lock() }
    val secondReadLatch = NSLock().apply { lock() }

    val thread = NSThread {
      threadStartedLatch.unlock()
      firstReadLatch.lock()
      threadLocal.set(1)
      valueFromThread = threadLocal.get()
      firstReadDoneLatch.unlock()
      secondReadLatch.lock()
      valueFromThread = threadLocal.get()
    }
    thread.start()

    // Wait for the other thread to start, then both threads set the value to something different
    // at the same time.
    threadStartedLatch.lock()
    firstReadLatch.unlock()
    threadLocal.set(2)

    // Wait for the background thread to finish setting value, then ensure that both threads see
    // independent values.
    firstReadDoneLatch.lock()
    assertEquals(1, valueFromThread)
    assertEquals(2, threadLocal.get())

    // Change the value in this thread then read it again from the background thread.
    threadLocal.set(3)
    secondReadLatch.unlock()
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
