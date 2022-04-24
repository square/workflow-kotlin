package com.squareup.workflow1

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmWorkerWorkflowTest {

  /**
   * This should be impossible, since the return type is non-nullable. However it is very easy to
   * accidentally create a mock using libraries like Mockito in unit tests that return null Flows.
   */
  @Test fun `runWorker throws when flow is null`() {
    val nullFlowWorker = NullFlowWorker()

    val error = runBlocking {
      assertFailsWith<NullPointerException> {
        runWorker(nullFlowWorker, "", NoopSink)
      }
    }

    assertEquals(
      "Worker NullFlowWorker.toString returned a null Flow. " +
        "If this is a test mock, make sure you mock the run() method!",
      error.message
    )
  }

  private object NoopSink : Sink<Any?> {
    override fun send(value: Any?) {
      // Noop
    }
  }
}
