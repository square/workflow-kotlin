package com.squareup.workflow1

import java.util.concurrent.CountDownLatch

/**
 * Returns the maximum number of threads that can be ran in parallel on the host system, rounded
 * down to the nearest even number, and at least 2.
 */
internal fun calculateSaturatingTestThreadCount(minThreads: Int) =
  Runtime.getRuntime().availableProcessors().let {
    if (it.mod(2) != 0) it - 1 else it
  }.coerceAtLeast(minThreads)

/**
 * Calls [CountDownLatch.await] in a loop until count is zero, even if the thread gets
 * interrupted.
 */
@Suppress("CheckResult")
internal fun CountDownLatch.awaitUntilDone() {
  while (count > 0) {
    try {
      await()
    } catch (e: InterruptedException) {
      // Continue
    }
  }
}
