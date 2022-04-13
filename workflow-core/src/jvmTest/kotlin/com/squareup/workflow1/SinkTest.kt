package com.squareup.workflow1

import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(
  ExperimentalCoroutinesApi::class,
  ExperimentalStdlibApi::class,
)
internal class SinkTest {

  private val sink = RecordingSink()

  @Test fun `collectToSink sends action`() = runTest {
    val flow = MutableStateFlow(1)
    val collector = launch {
      flow.collectToSink(sink) {
        action {
          state = "$props $state $it"
          setOutput("output: $it")
        }
      }
    }

    advanceUntilIdle()
    assertEquals(1, sink.actions.size)
    sink.actions.removeFirst()
      .let { action ->
        val (newState, output) = action.applyTo("props", "state")
        assertEquals("props state 1", newState)
        assertEquals("output: 1", output?.value)
      }
    assertTrue(sink.actions.isEmpty())

    flow.value = 2
    advanceUntilIdle()
    assertEquals(1, sink.actions.size)
    sink.actions.removeFirst()
      .let { action ->
        val (newState, output) = action.applyTo("props", "state")
        assertEquals("props state 2", newState)
        assertEquals("output: 2", output?.value)
      }

    collector.cancel()
  }

  @Test fun `collectToSink propagates backpressure`() {
    val channel = Channel<String>()
    val flow = channel.consumeAsFlow()
    // Used to assert ordering.
    val counter = AtomicInteger(0)
    val sentActions = mutableListOf<WorkflowAction<Unit, Unit, String>>()
    val sink = Sink<WorkflowAction<Unit, Unit, String>> {
      sentActions += it
    }

    runTest(UnconfinedTestDispatcher()) {
      val collectJob = launch {
        flow.collectToSink(sink) { action { setOutput(it) } }
      }

      val sendJob = launch(start = UNDISPATCHED) {
        assertEquals(0, counter.getAndIncrement())
        channel.send("a")
        assertEquals(1, counter.getAndIncrement())
        channel.send("b")
        assertEquals(4, counter.getAndIncrement())
        channel.close()
        assertEquals(5, counter.getAndIncrement())
      }
      advanceUntilIdle()
      assertEquals(2, counter.getAndIncrement())

      sentActions.removeFirst()
        .also {
          advanceUntilIdle()
          // Sender won't resume until we've _applied_ the action.
          assertEquals(3, counter.getAndIncrement())
        }
        .applyTo(Unit, Unit)
        .let { (_, output) ->
          assertEquals(6, counter.getAndIncrement())
          assertEquals("a", output?.value)
        }

      sentActions.removeFirst()
        .applyTo(Unit, Unit)
        .let { (_, output) ->
          assertEquals(7, counter.getAndIncrement())
          assertEquals("b", output?.value)
        }

      collectJob.cancel()
      sendJob.cancel()
    }
  }

  @Test fun `sendAndAwaitApplication applies action`() {
    var applications = 0
    val action = action<String, String, String> {
      applications++
      state = "$props $state applied"
      setOutput("output")
    }

    runTest {
      launch { sink.sendAndAwaitApplication(action) }
      advanceUntilIdle()

      val enqueuedAction = sink.actions.removeFirst()
      val (newState, output) = enqueuedAction.applyTo("props", "state")
      assertEquals(1, applications)
      assertEquals("props state applied", newState)
      assertEquals("output", output?.value)
    }
  }

  @Test fun `sendAndAwaitApplication suspends until after applied`() = runTest {
    var resumed = false
    val action = action<String, String, String> {
      assertFalse(resumed)
    }
    launch {
      sink.sendAndAwaitApplication(action)
      resumed = true
    }
    advanceUntilIdle()
    assertFalse(resumed)
    assertEquals(1, sink.actions.size)

    val enqueuedAction = sink.actions.removeFirst()

    withContext(StandardTestDispatcher(testScheduler)) {
      enqueuedAction.applyTo("props", "state")
      assertFalse(resumed)
    }

    advanceUntilIdle()
    assertTrue(resumed)
  }

  @Test fun `sendAndAwaitApplication doesn't apply action when cancelled while suspended`() =
    runTest {
      var applied = false
      val action = action<String, String, String> {
        applied = true
        fail()
      }
      val sendJob = launch { sink.sendAndAwaitApplication(action) }
      advanceUntilIdle()
      assertFalse(applied)
      assertEquals(1, sink.actions.size)

      val enqueuedAction = sink.actions.removeFirst()
      sendJob.cancel()
      advanceUntilIdle()
      val (newState, output) = enqueuedAction.applyTo("unused props", "state")

      assertFalse(applied)
      assertEquals("state", newState)
      assertNull(output)
    }

  private class RecordingSink : Sink<WorkflowAction<String, String, String>> {
    val actions = mutableListOf<WorkflowAction<String, String, String>>()

    override fun send(value: WorkflowAction<String, String, String>) {
      actions += value
    }
  }
}
