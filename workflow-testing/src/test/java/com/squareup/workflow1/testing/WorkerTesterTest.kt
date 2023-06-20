package com.squareup.workflow1.testing

import com.squareup.workflow1.Worker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerTesterTest {

  @Test fun `assertNoOutput succeeds in live flow without output`() {
    val worker = Worker.create<Unit> {
      suspendCancellableCoroutine { }
    }
    worker.test {
      assertNoOutput()
    }
  }

  @Test fun `assertNoOutput fails after worker finishes without emitting`() {
    val worker = Worker.finished<Unit>()
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertNoOutput()
      }
    }
    assertEquals(
      expected = "Expected no output, completion, or error to have been emitted.",
      actual = error.message
    )
  }

  @Test fun `assertNotFinished fails after worker finished`() {
    val worker = Worker.finished<Unit>()
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertNotFinished()
      }
    }
    assertEquals("Expected Worker to not be finished.", error.message)
  }

  @Test fun `assertNotFinished true while worker running`() {
    val worker = Worker.from<Unit> { suspendCancellableCoroutine {} }
    worker.test {
      assertNotFinished()
    }
  }

  @Test fun `assertNoOutput fails after worker emits`() {
    val worker = Worker.from { Unit }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertNoOutput()
      }
    }
    assertEquals(
      expected = "Expected no output, completion, or error to have been emitted.",
      actual = error.message
    )
  }

  @Test fun `assertFinished passes when worker finishes without emitting`() {
    val worker = Worker.finished<Unit>()
    worker.test {
      assertFinished()
    }
  }

  @Test fun `assertFinished fails when worker hasn't finished and hasn't emitted`() {
    val worker = Worker.from<Unit> { suspendCancellableCoroutine {} }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertFinished()
      }
    }
    assertEquals("Expected Worker to be finished.", error.message)
  }

  @Test fun `assertFinished fails when worker has emitted but hasn't finished`() {
    val worker = Worker.create {
      emit("output")
      suspendCancellableCoroutine {}
    }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertFinished()
      }
    }
    assertEquals("Expected Worker to be finished. Emitted outputs: [output]", error.message)
  }

  @Test fun `assertFinished failure includes all queued outputs`() {
    val worker = Worker.create {
      emit("foo")
      emit("bar")
      suspendCancellableCoroutine {}
    }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertFinished()
      }
    }
    assertEquals("Expected Worker to be finished. Emitted outputs: [foo, bar]", error.message)
  }

  @Test fun `nextOutput returns expected`() {
    val worker = Worker.create {
      emit("foo")
      emit("bar")
      suspendCancellableCoroutine {}
    }
    worker.test {
      val first = nextOutput()
      assertEquals("foo", first)
    }
  }

  @Test fun `cancelWorker cancels worker`() {
    var capturedContext: CoroutineContext? = null
    val worker = Worker.create {
      capturedContext = coroutineContext
      emit("foo")
      emit("bar")
      suspendCancellableCoroutine {}
    }
    worker.test {
      cancelWorker()
      assertFalse(capturedContext!!.isActive, "Expected worker to be canceled.")
    }
  }

  @Test fun `getException gives expected value`() {
    val expectedException = Throwable("My Special One.")
    val worker = Worker.create<Unit> {
      throw expectedException
    }
    worker.test {
      val exception = getException()
      assertEquals(expectedException.message, exception.message)
    }
  }

  @Test fun `skips delays`() {
    val worker = Worker.create {
      delay(1000)
      emit("foo")
    }
    worker.test(
      timeoutMs = 50L
    ) {
      val first = nextOutput()
      assertEquals("foo", first)
    }
  }

  @Test fun `times out when it should`() {
    val worker = Worker.create {
      emit("foo")
      suspendCancellableCoroutine {}
    }
    val error = assertFailsWith<AssertionError> {
      worker.test(
        timeoutMs = 5L
      ) {
        val first = nextOutput()
        assertEquals("foo", first)
        nextOutput()
      }
    }
    assertEquals(error.message!!, "No value produced in 5ms")
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `testCoroutineScheduler can control time for Worker`() {
    val worker = Worker.create {
      delay(300)
      emit("foo")
      emit("bar")
      suspendCancellableCoroutine {}
    }
    worker.test {
      assertNoOutput()
      testCoroutineScheduler.advanceTimeBy(300)
      testCoroutineScheduler.runCurrent()
      val first = nextOutput()
      assertEquals("foo", first)
    }
  }
}
