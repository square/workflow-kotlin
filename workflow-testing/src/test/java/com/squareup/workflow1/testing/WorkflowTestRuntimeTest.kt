@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow1.testing

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.internal.util.rethrowingUncaughtExceptions
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateful
import com.squareup.workflow1.stateless
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromWorkflowSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WorkflowTestRuntimeTest {

  private class ExpectedException(message: String? = null) : RuntimeException(message)

  @Test fun `propagates exception when block throws`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith {
          throw ExpectedException()
        }
      }
    }
  }

  @Test fun `propagates exception when workflow throws from render`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      throw ExpectedException()
    }

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith {
          // Nothing to do.
        }
      }
    }
  }

  @Test fun `propagates exception when Job is cancelled in test block`() {
    val job = Job()
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith(context = job) {
          job.cancel(CancellationException(null, ExpectedException()))
        }
      }
    }
  }

  @Test fun `propagates cancellation when Job is cancelled before starting`() {
    val job = Job().apply { cancel() }
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    rethrowingUncaughtExceptions {
      assertFailsWith<CancellationException> {
        workflow.launchForTestingFromStartWith(context = job) {
          awaitNextRendering()
        }
      }
    }
  }

  @Test fun `propagates cancellation when Job fails before starting`() {
    val job = Job().apply { cancel(CancellationException(null, ExpectedException())) }
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith(context = job) {
          // Nothing to do.
        }
      }
    }
  }

  @Test fun `propagates exception when workflow throws from initial state`() {
    val workflow = Workflow.stateful<Unit, Unit, Nothing, Unit>(
        initialState = { _, snapshot ->
          assertNull(snapshot)
          throw ExpectedException()
        },
        render = { _, _ -> fail() },
        snapshot = { fail() }
    )

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith {
          // Nothing to do.
        }
      }
    }
  }

  @Test fun `propagates exception when workflow throws from snapshot state`() {
    val workflow = Workflow.stateful<Unit, Nothing, Unit>(
        initialState = { snapshot -> assertNull(snapshot) },
        render = {},
        snapshot = { throw ExpectedException() }
    )

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith {
          // Nothing to do.
        }
      }
    }
  }

  @Test fun `propagates exception when workflow throws from restore state`() {
    val workflow = Workflow.stateful<Unit, Nothing, Unit>(
        initialState = { snapshot ->
          if (snapshot != null) {
            throw ExpectedException()
          }
        },
        render = {},
        snapshot = { null }
    )
    val snapshot = Snapshot.of("dummy snapshot")

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith(
            WorkflowTestParams(startFrom = StartFromWorkflowSnapshot(snapshot))
        ) {
          // Nothing to do.
        }
      }
    }
  }

  @Test fun `propagates exception when worker throws`() {
    val deferred = CompletableDeferred<Unit>()
    deferred.completeExceptionally(ExpectedException())
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(Worker.from { deferred.await() }) { fail("Shouldn't get here.") }
    }

    rethrowingUncaughtExceptions {
      assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith {
          // Nothing to do.
        }
      }
    }
  }

  @Test fun `does nothing when no outputs observed`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> {}

    rethrowingUncaughtExceptions {
      workflow.launchForTestingFromStartWith {
        // The workflow should never start.
      }
    }
  }

  @Test fun `workflow gets props from sendProps`() {
    val workflow = Workflow.stateless<String, Nothing, String> { props -> props }

    rethrowingUncaughtExceptions {
      workflow.launchForTestingFromStartWith("one") {
        assertEquals("one", awaitNextRendering())
        sendProps("two")
        assertEquals("two", awaitNextRendering())
      }
    }
  }

  // Props is a StateFlow, which means it behaves as if distinctUntilChange were applied.
  @Test fun `sendProps duplicate values don't trigger render passes`() {
    var renders = 0
    val props = "props"
    val workflow = Workflow.stateless<String, Nothing, Unit> {
      renders++
    }

    rethrowingUncaughtExceptions {
      workflow.launchForTestingFromStartWith(
          props,
          testParams = WorkflowTestParams(checkRenderIdempotence = false)
      ) {
        assertEquals(1, renders)

        sendProps(props)
        assertEquals(1, renders)

        sendProps(props)
        assertEquals(1, renders)
      }
    }
  }

  @Test fun `detects render side effects`() {
    var renderCount = 0
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderCount++
    }

    rethrowingUncaughtExceptions {
      workflow.launchForTestingFromStartWith {
        assertEquals(2, renderCount)
      }
    }
  }

  @Test fun `detects render side effects disabled`() {
    var renderCount = 0
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderCount++
    }

    rethrowingUncaughtExceptions {
      workflow.launchForTestingFromStartWith(testParams = WorkflowTestParams(checkRenderIdempotence = false)) {
        assertEquals(1, renderCount)
      }
    }
  }

  @Test fun `uncaught exceptions are suppressed when test body throws`() {
    val workflow = Workflow.stateless<Boolean, Nothing, Unit> { props ->
      // Can't throw on initial render pass, since that happens before starting the body.
      if (props) {
        throw ExpectedException("render")
      }
    }

    rethrowingUncaughtExceptions {
      val firstError = assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith(props = false) {
          sendProps(true)
          throw ExpectedException("test body")
        }
      }
      assertEquals("test body", firstError.message)
      val secondError = firstError.suppressed.single()
      assertTrue(secondError is ExpectedException)
      assertEquals("render", secondError.message)
    }
  }

  @Test fun `exceptions from first render pass skip test body`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      throw ExpectedException("render")
    }

    rethrowingUncaughtExceptions {
      val firstError = assertFailsWith<ExpectedException> {
        workflow.launchForTestingFromStartWith {
          throw ExpectedException("test body")
        }
      }
      assertEquals("render", firstError.message)
    }
  }

  @Test fun `empty lambda still executes the workflow`() {
    var itLived = false
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      itLived = true
    }
    workflow.launchForTestingFromStartWith {
    }
    assertTrue(itLived)
  }
}
