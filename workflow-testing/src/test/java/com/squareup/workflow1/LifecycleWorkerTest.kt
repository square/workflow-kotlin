@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow1

import com.squareup.workflow1.testing.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecycleWorkerTest {

  @Test fun `onStart called immediately`() {
    val property = System.getProperty("workflow.runtime")
    println("The runtime is: $property")
    var onStartCalled = false
    val worker = object : LifecycleWorker() {
      override fun onStarted() {
        onStartCalled = true
      }
    }

    assertFalse(onStartCalled)
    runBlocking {
      val job = worker.run()
        .launchIn(CoroutineScope(Unconfined))
      assertTrue(onStartCalled)

      // Don't hang the runBlocking block forever.
      job.cancel()
    }
  }

  @Test fun `onCancelled called on cancel`() {
    var onCancelledCalled = false
    val worker = object : LifecycleWorker() {
      override fun onStopped() {
        onCancelledCalled = true
      }
    }

    worker.test {
      assertFalse(onCancelledCalled)
      cancelWorker()

      assertTrue(onCancelledCalled)
    }
  }

  @Test fun `doesSameWorkAs compares concrete types`() {
    class LwA : LifecycleWorker()
    class LwB : LifecycleWorker()

    assertFalse(LwA().doesSameWorkAs(LwB()))
    assertTrue(LwA().doesSameWorkAs(LwA()))
  }
}
