@file:Suppress("EXPERIMENTAL_API_USAGE", "DEPRECATION")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.testing.WorkerSink
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

internal class WorkerCompositionIntegrationTest {

  private class ExpectedException : RuntimeException()

  @Test fun `worker started`() {
    var started = false
    val worker = Worker.create<Unit> { started = true }
    val workflow = Workflow.stateless<Boolean, Nothing, Unit> { props ->
      if (props) runningWorker(worker) { noAction() }
    }

    workflow.launchForTestingFromStartWith(false) {
      assertFalse(started)
      sendProps(true)
      awaitRuntimeSettled()
      assertTrue(started)
    }
  }

  @Test fun `worker cancelled when dropped`() {
    var cancelled = false
    val worker = object : LifecycleWorker() {
      override fun onStopped() {
        cancelled = true
      }
    }
    val workflow = Workflow.stateless<Boolean, Nothing, Unit> { props ->
      if (props) runningWorker(worker)
    }

    workflow.launchForTestingFromStartWith(true) {
      assertFalse(cancelled)
      sendProps(false)
      awaitRuntimeSettled()
      assertTrue(cancelled)
    }
  }

  @Test fun `worker only starts once over multiple renders`() {
    var starts = 0
    var stops = 0
    val worker = object : LifecycleWorker() {
      override fun onStarted() {
        starts++
      }

      override fun onStopped() {
        stops++
      }
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(worker)
    }

    workflow.launchForTestingFromStartWith {
      assertEquals(1, starts)
      assertEquals(0, stops)

      sendProps(Unit)
      awaitRuntimeSettled()
      assertEquals(1, starts)
      assertEquals(0, stops)

      sendProps(Unit)
      awaitRuntimeSettled()
      assertEquals(1, starts)
      assertEquals(0, stops)
    }
  }

  @Test fun `worker restarts`() {
    var starts = 0
    var stops = 0
    val worker = object : LifecycleWorker() {
      override fun onStarted() {
        starts++
      }

      override fun onStopped() {
        stops++
      }
    }
    val workflow = Workflow.stateless<Boolean, Nothing, Unit> { props ->
      if (props) runningWorker(worker)
    }

    workflow.launchForTestingFromStartWith(false) {
      assertEquals(0, starts)
      assertEquals(0, stops)

      sendProps(true)
      awaitRuntimeSettled()
      assertEquals(1, starts)
      assertEquals(0, stops)

      sendProps(false)
      awaitRuntimeSettled()
      assertEquals(1, starts)
      assertEquals(1, stops)

      sendProps(true)
      awaitRuntimeSettled()
      assertEquals(2, starts)
      assertEquals(1, stops)
    }
  }

  @Test fun `runningWorker gets output`() {
    val worker = WorkerSink<String>("")
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(worker) { action("") { setOutput(it) } }
    }

    workflow.launchForTestingFromStartWith {
      assertFalse(this.hasOutput)

      worker.send("foo")

      assertEquals("foo", awaitNextOutput())
    }
  }

  @Test fun `runningWorker gets error`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(Worker.from<Unit> { throw ExpectedException() }) {
        action("") { }
      }
    }

    assertFailsWith<ExpectedException> {
      workflow.launchForTestingFromStartWith {
        assertFalse(this.hasOutput)

        awaitNextOutput()
      }
    }
  }

  @Test fun `runningWorker does nothing when worker finished`() {
    val channel = Channel<Unit>()
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(
        channel.consumeAsFlow()
          .asWorker()
      ) { fail("Expected handler to not be invoked.") }
    }

    workflow.launchForTestingFromStartWith {
      channel.close()

      assertFailsWith<TimeoutCancellationException> {
        // There should never be any outputs, so this should timeout.
        awaitNextOutput(timeoutMs = 100)
      }
    }
  }

  // See https://github.com/square/workflow/issues/261.
  @Test fun `runningWorker handler closes over latest state`() {
    val triggerOutput = WorkerSink<Unit>("")

    val incrementState = action<Unit, Int, Int>("") {
      state += 1
    }

    val workflow = Workflow.stateful(
      initialState = 0,
      render = { _ ->
        runningWorker(triggerOutput) { action("") { setOutput(state) } }

        return@stateful { actionSink.send(incrementState) }
      }
    )

    workflow.launchForTestingFromStartWith {
      triggerOutput.send(Unit)
      assertEquals(0, awaitNextOutput())

      awaitNextRendering()
        .invoke()
      triggerOutput.send(Unit)

      assertEquals(1, awaitNextOutput())

      awaitNextRendering()
        .invoke()
      triggerOutput.send(Unit)

      assertEquals(2, awaitNextOutput())
    }
  }

  @Test fun `runningWorker doesn't throw when worker finishes`() {
    // No-op worker, completes immediately.
    val worker = Worker.from { }
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(worker) {
        action("") { }
      }
    }

    workflow.launchForTestingFromStartWith {
      assertFailsWith<TimeoutCancellationException> {
        awaitNextOutput(timeoutMs = 100)
      }
    }
  }

  @Test fun `worker context job is ignored`() {
    val worker = Worker.from { coroutineContext }
    val leafWorkflow = Workflow.stateless<Unit, CoroutineContext, Unit> {
      runningWorker(worker) { context -> action("") { setOutput(context) } }
    }
    val workflow = Workflow.stateless<Unit, CoroutineContext, Unit> {
      renderChild(leafWorkflow) { action("") { setOutput(it) } }
    }
    val job = Job()

    workflow.launchForTestingFromStartWith(context = job) {
      val actualWorkerContext = awaitNextOutput()
      assertNotSame(job, actualWorkerContext[Job])
    }
  }

  @OptIn(WorkflowExperimentalRuntime::class)
  @Test
  fun `worker context is used for workers`() {
    if (JvmTestRuntimeConfigTools.getTestRuntimeConfig()
        .contains(RuntimeConfigOptions.WORK_STEALING_DISPATCHER)
    ) {
      // This test does not work when the WSD is wrapping the dispatcher,
      // and that is internal so ideally we don't expose it just for this test.
      return
    }
    val worker = Worker.from { coroutineContext }
    val leafWorkflow = Workflow.stateless<Unit, CoroutineContext, Unit> {
      runningWorker(worker) { context -> action("") { setOutput(context) } }
    }
    val workflow = Workflow.stateless<Unit, CoroutineContext, Unit> {
      renderChild(leafWorkflow) { action("") { setOutput(it) } }
    }
    val dispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
      override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        Unconfined.isDispatchNeeded(context)

      override fun dispatch(
        context: CoroutineContext,
        block: Runnable
      ) = Unconfined.dispatch(context, block)
    }

    workflow.launchForTestingFromStartWith(context = dispatcher) {
      val actualWorkerContext = awaitNextOutput()
      assertSame(dispatcher, actualWorkerContext[ContinuationInterceptor])
    }
  }
}
