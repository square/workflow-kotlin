package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.tracing.ConfigSnapshot
import com.squareup.workflow1.tracing.RuntimeUpdates
import com.squareup.workflow1.tracing.WorkflowRuntimeLoopListener
import com.squareup.workflow1.tracing.WorkflowRuntimeMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that verify end-to-end tracing functionality by using the real workflow
 * runtime with renderWorkflowIn and WorkflowRuntimeMonitor as an interceptor.
 */
internal class WorkflowTracingIntegrationTest {

  private val runtimeName = "IntegrationTestRuntime"
  private val runtimeConfig = JvmTestRuntimeConfigTools.getTestRuntimeConfig()

  @Test
  fun `integration test - root workflow creation is fully traced`() =
    runTest {
      val runtimeLoopMutex = Mutex(locked = true)
      val runtimeLoopListener = WorkflowRuntimeLoopListener { _, _ ->
        runtimeLoopMutex.unlock()
      }
      val fakeTrace = FakeSafeTrace()
      val papaTracer = WorkflowPapaTracer(fakeTrace)
      val monitor = WorkflowRuntimeMonitor(
        runtimeName = runtimeName,
        workflowRuntimeTracers = listOf(papaTracer),
        runtimeLoopListener = runtimeLoopListener,
      )

      val props = MutableStateFlow("initial")
      val workflow = TestWorkflow()

      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = props,
        interceptors = listOf(monitor),
        runtimeConfig = runtimeConfig,
      ) {}

      runtimeLoopMutex.lock()

      // Verify initial rendering
      assertEquals("state: initial", renderings.value.rendering)

      // Verify comprehensive tracing occurred
      val traceCalls = fakeTrace.traceCalls
      assertTrue(traceCalls.isNotEmpty())

      // Should have async section for workflow session
      assertTrue(traceCalls.any { it.type == "beginAsyncSection" && it.name!!.contains("WKF") })

      // Should have initial state tracing
      assertTrue(
        traceCalls.any { it.type == "beginSection" && it.label!!.contains("InitialState") }
      )

      // Should have render pass tracing
      assertTrue(traceCalls.any { it.type == "beginSection" && it.label!!.contains("RENDER") })

      // Should have root creation render tracing
      assertTrue(
        traceCalls.any { it.type == "beginSection" && it.label!!.contains("CREATE_RENDER") }
      )

      // Should have matching end sections
      val beginSections = traceCalls.count { it.type == "beginSection" }
      val endSections = traceCalls.count { it.type == "endSection" }
      assertEquals(
        beginSections,
        endSections,
        "All begin sections should have matching end sections"
      )
    }

  @Test
  fun `integration test - props change is fully traced`() = runTest {

    val runtimeLoopMutex = Mutex(locked = true)
    val runtimeLoopListener = WorkflowRuntimeLoopListener { _, _ ->
      runtimeLoopMutex.unlock()
    }
    val fakeTrace = FakeSafeTrace()
    val papaTracer = WorkflowPapaTracer(fakeTrace)
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(papaTracer),
      runtimeLoopListener = runtimeLoopListener,
    )

    val props = MutableStateFlow("initial")
    val workflow = TestWorkflow()

    val renderings = renderWorkflowIn(
      workflow = workflow,
      scope = backgroundScope,
      props = props,
      interceptors = listOf(monitor),
      runtimeConfig = runtimeConfig,
    ) {}

    // Wait for the lock (runtime loop complete).
    runtimeLoopMutex.lock()

    assertEquals("state: initial", renderings.value.rendering)

    fakeTrace.clearTraceCalls()

    // Change props to trigger new render
    props.value = "updated"

    // Wait for the lock (runtime loop complete).
    runtimeLoopMutex.lock()

    assertEquals("state: updated", renderings.value.rendering)

    val traceCalls = fakeTrace.traceCalls

    // Should have props change render tracing
    assertTrue(traceCalls.any { it.type == "beginSection" && it.label!!.contains("PROPS_RENDER") })

    // Should have render pass tracing
    assertTrue(traceCalls.any { it.type == "beginSection" && it.label!!.contains("RENDER") })

    // Should have info sections for props change
    assertTrue(traceCalls.any { it.type == "logSection" && it.label!!.contains("RootWFProps") })
  }

  @Test
  fun `integration test - action-triggered render is fully traced`() =
    runTest {
      val runtimeLoopMutex = Mutex(locked = true)
      val runtimeLoopListener = WorkflowRuntimeLoopListener { _, _ ->
        runtimeLoopMutex.unlock()
      }
      val fakeTrace = FakeSafeTrace()
      val papaTracer = WorkflowPapaTracer(fakeTrace)
      val monitor = WorkflowRuntimeMonitor(
        runtimeName = runtimeName,
        workflowRuntimeTracers = listOf(papaTracer),
        runtimeLoopListener = runtimeLoopListener,
      )

      val props = MutableStateFlow("initial")
      val workflow = InteractiveTestWorkflow()

      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = props,
        interceptors = listOf(monitor),
        runtimeConfig = runtimeConfig,
      ) {}

      runtimeLoopMutex.lock()

      val initialRendering = renderings.value.rendering
      assertEquals("state: initial", initialRendering.text)

      fakeTrace.clearTraceCalls()

      // Trigger an action by calling the callback
      initialRendering.onAction("action-triggered")
      runtimeLoopMutex.lock()

      val updatedRendering = renderings.value.rendering
      assertEquals("state: action-triggered", updatedRendering.text)

      val traceCalls = fakeTrace.traceCalls

      // Should have action render tracing
      assertTrue(
        traceCalls.any { it.type == "beginSection" && it.label!!.contains("MAYBE_RENDER") }
      )

      // Should have render pass tracing
      assertTrue(traceCalls.any { it.type == "beginSection" && it.label!!.contains("RENDER") })

      // Verify sections are properly closed
      val beginSections = traceCalls.count { it.type == "beginSection" }
      val endSections = traceCalls.count { it.type == "endSection" }
      assertEquals(beginSections, endSections)
    }

  @Test
  fun `integration test - worker triggers render correctly traced`() =
    runTest {
      val runtimeLoopMutex = Mutex(locked = true)
      val runtimeLoopListener = WorkflowRuntimeLoopListener { _, _ ->
        runtimeLoopMutex.unlock()
      }
      val fakeTrace = FakeSafeTrace()
      val papaTracer = WorkflowPapaTracer(fakeTrace)
      val monitor = WorkflowRuntimeMonitor(
        runtimeName = runtimeName,
        workflowRuntimeTracers = listOf(papaTracer),
        runtimeLoopListener = runtimeLoopListener
      )

      val props = MutableStateFlow("initial")
      val workflow = WorkerTestWorkflow()

      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = props,
        interceptors = listOf(monitor),
        runtimeConfig = runtimeConfig,
      ) {}

      runtimeLoopMutex.lock()

      val initialRendering = renderings.value.rendering
      assertEquals("state: initial", initialRendering.text)

      fakeTrace.clearTraceCalls()

      // Trigger the worker by sending a value
      initialRendering.triggerWorker("worker-result")
      runtimeLoopMutex.lock()

      val updatedRendering = renderings.value.rendering
      assertEquals("state: worker-result", updatedRendering.text)

      val traceCalls = fakeTrace.traceCalls

      // Should have action render tracing from worker
      assertTrue(
        traceCalls.any { it.type == "beginSection" && it.label!!.contains("MAYBE_RENDER") }
      )

      // Should have render pass tracing
      assertTrue(traceCalls.any { it.type == "beginSection" && it.label!!.contains("RENDER") })
    }

  @Test
  fun `integration test - child workflow renders are traced`() =
    runTest {
      val runtimeLoopMutex = Mutex(locked = true)
      val runtimeLoopListener = WorkflowRuntimeLoopListener { _, _ ->
        runtimeLoopMutex.unlock()
      }
      val fakeTrace = FakeSafeTrace()
      val papaTracer = WorkflowPapaTracer(fakeTrace)
      val monitor = WorkflowRuntimeMonitor(
        runtimeName = runtimeName,
        workflowRuntimeTracers = listOf(papaTracer),
        runtimeLoopListener = runtimeLoopListener
      )

      val props = MutableStateFlow("parent-initial")
      val workflow = ParentWorkflow()

      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = props,
        interceptors = listOf(monitor),
        runtimeConfig = runtimeConfig,
      ) {}

      runtimeLoopMutex.lock()

      val initialRendering = renderings.value.rendering
      assertEquals("parent: parent-initial, child: child-parent-initial", initialRendering.text)

      val traceCalls = fakeTrace.traceCalls

      // Should have async sections for both parent and child workflows
      val asyncSections = traceCalls.filter { it.type == "beginAsyncSection" }
      assertTrue(
        asyncSections.size >= 2,
        "Should have at least 2 async sections (parent and child)"
      )

      // Should have render sections for both workflows
      val renderSections =
        traceCalls.filter { it.type == "beginSection" && it.label!!.contains("RENDER") }
      assertTrue(renderSections.size >= 2, "Should have at least 2 render sections")

      // Should have initial state tracing for both workflows
      val initialStateSections =
        traceCalls.filter { it.type == "beginSection" && it.label!!.contains("InitialState") }
      assertTrue(initialStateSections.size >= 2, "Should have at least 2 initial state sections")
    }

  @Test
  fun `integration test - runtime loop processing is traced`() =
    runTest {
      val runtimeLoopMutex = Mutex(locked = true)
      val fakeTrace = FakeSafeTrace()
      val papaTracer = WorkflowPapaTracer(fakeTrace)
      val runtimeListener = TestWorkflowRuntimeLoopListener(runtimeLoopMutex)
      val monitor = WorkflowRuntimeMonitor(
        runtimeName = runtimeName,
        workflowRuntimeTracers = listOf(papaTracer),
        runtimeLoopListener = runtimeListener
      )

      val props = MutableStateFlow("initial")
      val workflow = TestWorkflow()

      renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope,
        props = props,
        interceptors = listOf(monitor),
        runtimeConfig = runtimeConfig,
      ) {}

      runtimeLoopMutex.lock()

      // Verify that runtime loop listener was called
      assertTrue(runtimeListener.onRuntimeLoopSettledCalled)
      assertNotNull(runtimeListener.runtimeUpdatesReceived)

      val traceCalls = fakeTrace.traceCalls

      // Should have summary information from runtime loop tick
      assertTrue(traceCalls.any { it.type == "logSection" && it.label!!.contains("SUM") })

      // Should include configuration information
      assertTrue(traceCalls.any { it.type == "logSection" && it.label!!.contains("Config:") })
    }

  // Test helper classes
  private class TestWorkflow : StatefulWorkflow<String, String, Nothing, String>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String {
      return props
    }

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String {
      return new
    }

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, Nothing>
    ): String {
      return "state: $renderState"
    }

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }

  private data class InteractiveRendering(
    val text: String,
    val onAction: (String) -> Unit
  )

  private class InteractiveTestWorkflow : StatefulWorkflow<String, String, Nothing, InteractiveRendering>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = props

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = new

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, Nothing>
    ): InteractiveRendering {
      return InteractiveRendering(
        text = "state: $renderState",
        onAction = { newState ->
          context.actionSink.send(action("user-action") { state = newState })
        }
      )
    }

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }

  private data class WorkerRendering(
    val text: String,
    val triggerWorker: (String) -> Unit
  )

  private class WorkerTestWorkflow : StatefulWorkflow<String, String, Nothing, WorkerRendering>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = props

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = new

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, Nothing>
    ): WorkerRendering {
      // Create a worker that can be triggered externally
      val triggerChannel = Channel<String>(capacity = Channel.UNLIMITED)

      val triggerFunction = { value: String ->
        triggerChannel.trySend(value)
        Unit
      }

      context.runningWorker(
        triggerChannel.receiveAsFlow().asWorker()
      ) { result ->
        action("worker-action") { state = result }
      }

      return WorkerRendering(
        text = "state: $renderState",
        triggerWorker = triggerFunction
      )
    }

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }

  private data class ParentRendering(val text: String)

  private class ParentWorkflow : StatefulWorkflow<String, String, Nothing, ParentRendering>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = props

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = new

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, Nothing>
    ): ParentRendering {
      val childRendering = context.renderChild(
        child = ChildWorkflow(),
        props = "child-$renderState"
      ) {
        // Child doesn't emit output in this test
        WorkflowAction.noAction()
      }

      return ParentRendering("parent: $renderState, child: $childRendering")
    }

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }

  private class ChildWorkflow : StatefulWorkflow<String, String, Nothing, String>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = props

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = new

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, Nothing>
    ): String = renderState

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }

  private class TestWorkflowRuntimeLoopListener(
    val runtimeLoopMutex: Mutex,
  ) : WorkflowRuntimeLoopListener {
    var onRuntimeLoopSettledCalled = false
    var runtimeUpdatesReceived: RuntimeUpdates? = null

    override fun onRuntimeLoopSettled(
      configSnapshot: ConfigSnapshot,
      runtimeUpdates: RuntimeUpdates
    ) {
      runtimeLoopMutex.unlock()
      onRuntimeLoopSettledCalled = true
      runtimeUpdatesReceived = runtimeUpdates
    }
  }
}
