package com.squareup.workflow1.tracing

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderPassSkipped
import com.squareup.workflow1.WorkflowInterceptor.RenderingConflated
import com.squareup.workflow1.WorkflowInterceptor.RenderingProduced
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.WorkflowInterceptor.RuntimeUpdate
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.identifier
import com.squareup.workflow1.tracing.RenderCause.RootCreation
import com.squareup.workflow1.tracing.RenderCause.RootPropsChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class WorkflowRuntimeMonitorTest {

  private val runtimeName = "TestRuntime"
  private val fakeRuntimeTracer = TestWorkflowRuntimeTracer()
  private val fakeRenderPassTracker = TestWorkflowRenderPassTracker()
  private val fakeRuntimeLoopListener = TestWorkflowRuntimeLoopListener()

  @Test
  fun `monitor can be instantiated with empty dependencies`() {
    val monitor = WorkflowRuntimeMonitor(runtimeName)
    assertNotNull(monitor)
    assertEquals(runtimeName, monitor.runtimeName)
  }

  @Test
  fun `monitor can be instantiated with all dependencies`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      renderPassTracker = fakeRenderPassTracker,
      runtimeLoopListener = fakeRuntimeLoopListener
    )
    assertNotNull(monitor)
    assertEquals(runtimeName, monitor.runtimeName)
  }

  @Test
  fun `onSessionStarted handles root workflow session`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    monitor.onSessionStarted(testScope, rootSession)

    assertTrue(fakeRuntimeTracer.onWorkflowSessionStartedCalled)
    assertEquals(1, monitor.workflowSessionInfo.size)
    assertEquals(1, monitor.renderIncomingCauses.size)
    assertTrue(monitor.renderIncomingCauses.first() is RootCreation)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `onSessionStarted captures runtime context`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val testCoroutineDispatcher = UnconfinedTestDispatcher()
    val rootSession = testWorkflow.createRootSession(testCoroutineDispatcher)
    val testScope = TestScope(testCoroutineDispatcher)

    monitor.onSessionStarted(testScope, rootSession)

    assertEquals(testCoroutineDispatcher, monitor.configSnapshot.runtimeDispatch)
  }

  @Test
  fun `onSessionStarted handles child workflow session`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val childSession = testWorkflow.createChildSession(rootSession)
    val testScope = TestScope()

    // Start root first to establish rendering context
    monitor.onSessionStarted(testScope, rootSession)
    // Simulate rendering
    monitor.currentRenderCause = RootCreation(runtimeName, "TestWorkflow")

    monitor.onSessionStarted(testScope, childSession)

    assertEquals(2, fakeRuntimeTracer.onWorkflowSessionStartedCallCount)
    assertEquals(2, monitor.workflowSessionInfo.size)
  }

  @Test
  fun `onInitialState delegates to tracers and handles root workflow`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    val result = monitor.onInitialState(
      props = "testProps",
      snapshot = null,
      workflowScope = testScope,
      proceed = { _, _, _ -> "initialState" },
      session = rootSession
    )

    assertEquals("initialState", result)
    assertTrue(fakeRuntimeTracer.onInitialStateCalled)
  }

  @Test
  fun `onPropsChanged delegates to tracers`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val session = testWorkflow.createRootSession()

    val result = monitor.onPropsChanged(
      old = "oldProps",
      new = "newProps",
      state = "currentState",
      proceed = { _, _, state -> state },
      session = session
    )

    assertEquals("currentState", result)
    assertTrue(fakeRuntimeTracer.onPropsChangedCalled)
  }

  @Test
  fun `onRenderAndSnapshot handles root workflow rendering`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      renderPassTracker = fakeRenderPassTracker
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()
    val initialProps = "props"

    // Initialize session and set up render cause
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState(initialProps, null, testScope, { _, _, _ -> "state" }, rootSession)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // Use the same props reference to simulate normal rendering (not props change)
    val result = monitor.onRenderAndSnapshot(
      renderProps = initialProps,
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    assertEquals(renderingAndSnapshot, result)
    assertTrue(fakeRuntimeTracer.onRenderAndSnapshotCalled)
    assertEquals(1, fakeRenderPassTracker.renderPassCount)
    assertNotNull(monitor.previousRenderCause)
  }

  @Test
  fun `onRenderAndSnapshot handles props change`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize with initial props
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState("initialProps", null, testScope, { _, _, _ -> "state" }, rootSession)

    // Clear the initial render causes
    monitor.renderIncomingCauses.clear()

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // Render with different props - this should trigger props change detection
    val result = monitor.onRenderAndSnapshot(
      renderProps = "newProps",
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    assertEquals(renderingAndSnapshot, result)
    assertTrue(monitor.renderIncomingCauses.any { it is RootPropsChanged })
    assertTrue(fakeRuntimeTracer.onRootPropsChangedCalled)
  }

  @Test
  fun `onRender creates monitoring interceptor`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()
    val mockContext = TestBaseRenderContext()

    // Set up monitoring state
    monitor.onSessionStarted(testScope, rootSession)
    monitor.currentRenderCause = RootCreation(runtimeName, "TestWorkflow")

    var interceptorReceived: Any? = null
    val result = monitor.onRender(
      renderProps = "props",
      renderState = "state",
      context = mockContext,
      proceed = { _, _, interceptor ->
        interceptorReceived = interceptor
        "rendered"
      },
      session = rootSession
    )

    assertEquals("rendered", result)
    assertNotNull(interceptorReceived)
    assertTrue(fakeRuntimeTracer.onRenderCalled)
  }

  @Test
  fun `onSnapshotStateWithChildren delegates to tracers`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val session = testWorkflow.createRootSession()
    val treeSnapshot = TreeSnapshot.forRootOnly(null)

    val result = monitor.onSnapshotStateWithChildren(
      proceed = { treeSnapshot },
      session = session
    )

    assertEquals(treeSnapshot, result)
    assertTrue(fakeRuntimeTracer.onSnapshotStateWithChildrenCalled)
  }

  @Test
  fun `onRuntimeUpdate handles RenderPassSkipped`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session to set up configSnapshot
    monitor.onSessionStarted(testScope, rootSession)

    // Set up a render cause to be marked as previous
    monitor.renderIncomingCauses.add(RootCreation(runtimeName, "TestWorkflow"))

    monitor.onRuntimeUpdate(RenderPassSkipped)

    assertTrue(fakeRuntimeTracer.onRuntimeUpdateEnhancedCalled)
    assertNotNull(monitor.previousRenderCause)
  }

  @Test
  fun `onRuntimeUpdate handles RuntimeLoopSettled`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      runtimeLoopListener = fakeRuntimeLoopListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize to set up config snapshot
    monitor.onSessionStarted(testScope, rootSession)

    monitor.onRuntimeUpdate(RuntimeSettled)

    assertTrue(fakeRuntimeTracer.onRuntimeUpdateEnhancedCalled)
    assertTrue(fakeRuntimeLoopListener.onRuntimeLoopSettledCalled)
    assertTrue(monitor.renderIncomingCauses.isEmpty())
  }

  @Test
  fun `onRuntimeUpdate handles RenderingConflated and RenderingProduced`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session to set up configSnapshot
    monitor.onSessionStarted(testScope, rootSession)

    monitor.onRuntimeUpdate(RenderingConflated)
    monitor.onRuntimeUpdate(RenderingProduced)

    // These should complete without error and call tracers
    assertTrue(fakeRuntimeTracer.onRuntimeUpdateEnhancedCallCount >= 2)
  }

  @Test
  fun `addRuntimeUpdate adds to runtime updates`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName,
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session to set up configSnapshot
    monitor.onSessionStarted(testScope, rootSession)

    val logLine = UiUpdateLogLine("Test update")

    monitor.addRuntimeUpdate(logLine)
    monitor.onRuntimeUpdate(RuntimeSettled)

    assertContains(runtimeListener.runtimeUpdatesReceived!!.readAndClear(), logLine)
  }

  @Test
  fun `RenderPassSkipped adds to runtime updates`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName,
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session to set up configSnapshot
    monitor.onSessionStarted(testScope, rootSession)

    // Set up a render cause to be marked as previous
    monitor.renderIncomingCauses.add(RootCreation(runtimeName, "TestWorkflow"))

    monitor.onRuntimeUpdate(RenderPassSkipped)
    monitor.onRuntimeUpdate(RuntimeSettled)

    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    assertTrue(updates.any { it is SkipLogLine })
  }

  @Test
  fun `RenderingConflated does not add to runtime updates`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName,
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session to set up configSnapshot
    monitor.onSessionStarted(testScope, rootSession)

    monitor.onRuntimeUpdate(RenderingConflated)
    monitor.onRuntimeUpdate(RuntimeSettled)

    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    // RenderingConflated is commented out in the implementation, so no log line should be added
    assertFalse(updates.any { it.toString().contains("Conflated") })
  }

  @Test
  fun `RenderingProduced does not add to runtime updates`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName,
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session to set up configSnapshot
    monitor.onSessionStarted(testScope, rootSession)

    monitor.onRuntimeUpdate(RenderingProduced)
    monitor.onRuntimeUpdate(RuntimeSettled)

    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    // RenderingProduced is commented out in the implementation, so no log line should be added
    assertFalse(updates.any { it.toString().contains("Produced") })
  }

  @Test
  fun `onRenderAndSnapshot adds RenderLogLine to runtime updates`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName,
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()
    val initialProps = "props"

    // Initialize session and set up render cause
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState(initialProps, null, testScope, { _, _, _ -> "state" }, rootSession)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // Perform render and snapshot
    monitor.onRenderAndSnapshot(
      renderProps = initialProps,
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    monitor.onRuntimeUpdate(RuntimeSettled)

    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    assertTrue(updates.any { it is RenderLogLine })
  }

  @Test
  fun `isRoot property is correct`() {
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val childSession = testWorkflow.createChildSession(rootSession)

    assertTrue(rootSession.isRootWorkflow)
    assertFalse(childSession.isRootWorkflow)
  }

  @Test
  fun `render pass tracker receives correct RenderPassInfo for root creation`() {
    val renderPassTracker = TestWorkflowRenderPassTracker()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      renderPassTracker = renderPassTracker
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()
    val initialProps = "props"

    // Initialize session and set up render cause
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState(initialProps, null, testScope, { _, _, _ -> "state" }, rootSession)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // Perform render and snapshot
    monitor.onRenderAndSnapshot(
      renderProps = initialProps,
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    // Verify render pass was recorded
    assertEquals(1, renderPassTracker.renderPassCount)
    assertNotNull(renderPassTracker.renderPassInfoReceived)

    val renderPassInfo = renderPassTracker.renderPassInfoReceived!!
    assertEquals(runtimeName, renderPassInfo.runnerName)
    assertTrue(renderPassInfo.renderCause is RootCreation)
    assertTrue(renderPassInfo.durationUptime.inWholeNanoseconds > 0)
  }

  @Test
  fun `render pass tracker receives correct RenderPassInfo for props change`() {
    val renderPassTracker = TestWorkflowRenderPassTracker()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      renderPassTracker = renderPassTracker
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize with initial props
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState("initialProps", null, testScope, { _, _, _ -> "state" }, rootSession)

    // Clear the initial render causes
    monitor.renderIncomingCauses.clear()

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // Reset tracker to ensure we're only measuring the props change render
    renderPassTracker.renderPassCount = 0
    renderPassTracker.renderPassInfoReceived = null

    // Render with different props - this should trigger props change detection
    monitor.onRenderAndSnapshot(
      renderProps = "newProps",
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    // Verify render pass was recorded with props change cause
    assertEquals(1, renderPassTracker.renderPassCount)
    assertNotNull(renderPassTracker.renderPassInfoReceived)

    val renderPassInfo = renderPassTracker.renderPassInfoReceived!!
    assertEquals(runtimeName, renderPassInfo.runnerName)
    assertTrue(renderPassInfo.renderCause is RootPropsChanged)
    assertTrue(renderPassInfo.durationUptime.inWholeNanoseconds > 0)
  }

  @Test
  fun `render pass tracker receives correct RenderPassInfo for action-triggered render`() {
    val renderPassTracker = TestWorkflowRenderPassTracker()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      renderPassTracker = renderPassTracker
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()
    val initialProps = "props"

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState(initialProps, null, testScope, { _, _, _ -> "state" }, rootSession)

    // Simulate an action-triggered render by adding a callback render cause
    monitor.renderIncomingCauses.clear()
    val callbackCause = RenderCause.Callback("testAction", "TestWorkflow")
    monitor.renderIncomingCauses.add(callbackCause)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // Reset tracker to ensure we're only measuring the action-triggered render
    renderPassTracker.renderPassCount = 0
    renderPassTracker.renderPassInfoReceived = null

    // Perform render and snapshot
    monitor.onRenderAndSnapshot(
      renderProps = initialProps,
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    // Verify render pass was recorded with callback cause
    assertEquals(1, renderPassTracker.renderPassCount)
    assertNotNull(renderPassTracker.renderPassInfoReceived)

    val renderPassInfo = renderPassTracker.renderPassInfoReceived!!
    assertEquals(runtimeName, renderPassInfo.runnerName)
    assertTrue(renderPassInfo.renderCause is RenderCause.Callback)
    assertEquals("testAction", (renderPassInfo.renderCause as RenderCause.Callback).actionName)
    assertEquals("TestWorkflow", (renderPassInfo.renderCause as RenderCause.Callback).workflowName)
    assertTrue(renderPassInfo.durationUptime.inWholeNanoseconds > 0)
  }

  @Test
  fun `render pass tracker tracks multiple render passes`() {
    val renderPassTracker = TestWorkflowRenderPassTracker()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      renderPassTracker = renderPassTracker
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()
    val initialProps = "props"

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)
    monitor.onInitialState(initialProps, null, testScope, { _, _, _ -> "state" }, rootSession)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    // First render pass (root creation)
    monitor.onRenderAndSnapshot(
      renderProps = initialProps,
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    assertEquals(1, renderPassTracker.renderPassCount)
    assertTrue(renderPassTracker.renderPassInfoReceived!!.renderCause is RootCreation)

    // Second render pass (props change)
    monitor.renderIncomingCauses.clear()
    monitor.onRenderAndSnapshot(
      renderProps = "differentProps",
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    assertEquals(2, renderPassTracker.renderPassCount)
    assertTrue(renderPassTracker.renderPassInfoReceived!!.renderCause is RootPropsChanged)

    // Third render pass (action callback)
    monitor.renderIncomingCauses.clear()
    monitor.renderIncomingCauses.add(RenderCause.Callback("anotherAction", "TestWorkflow"))
    monitor.onRenderAndSnapshot(
      // Same props to avoid props change detection
      renderProps = "differentProps",
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    assertEquals(3, renderPassTracker.renderPassCount)
    assertTrue(renderPassTracker.renderPassInfoReceived!!.renderCause is RenderCause.Callback)
  }

  @Test
  fun `onSessionCancelled logs dropped actions`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)

    // Create some test actions
    val action1 = TestAction("action1")
    val action2 = TestAction("action2")
    val droppedActions = listOf(action1, action2)

    // Call onSessionCancelled with dropped actions
    monitor.onSessionCancelled(
      cause = null,
      droppedActions = droppedActions,
      session = rootSession
    )

    // Settle the runtime to flush updates
    monitor.onRuntimeUpdate(RuntimeSettled)

    // Verify dropped actions were logged
    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    val droppedLogLines = updates.filterIsInstance<ActionDroppedLogLine>()

    assertEquals(2, droppedLogLines.size)
    assertEquals("action1", droppedLogLines[0].actionName)
    assertEquals("action2", droppedLogLines[1].actionName)
  }

  @Test
  fun `onSessionCancelled logs no actions when list is empty`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)

    // Call onSessionCancelled with empty dropped actions list
    monitor.onSessionCancelled(
      cause = null,
      droppedActions = emptyList<WorkflowAction<String, String, String>>(),
      session = rootSession
    )

    // Settle the runtime to flush updates
    monitor.onRuntimeUpdate(RuntimeSettled)

    // Verify no dropped actions were logged
    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    val droppedLogLines = updates.filterIsInstance<ActionDroppedLogLine>()

    assertEquals(0, droppedLogLines.size)
  }

  @Test
  fun `onSessionCancelled calls tracer onWorkflowSessionStopped`() {
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer)
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)

    // Verify session is tracked
    assertEquals(1, monitor.workflowSessionInfo.size)

    // Call onSessionCancelled
    monitor.onSessionCancelled(
      cause = null,
      droppedActions = emptyList<WorkflowAction<String, String, String>>(),
      session = rootSession
    )

    // Verify session was removed from tracking
    assertEquals(0, monitor.workflowSessionInfo.size)
    assertTrue(fakeRuntimeTracer.onWorkflowSessionStoppedCalled)
  }

  @Test
  fun `onSessionCancelled with CancellationException logs dropped actions`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      workflowRuntimeTracers = listOf(fakeRuntimeTracer),
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)

    // Create test actions
    val action1 = TestAction("cancelledAction")
    val droppedActions = listOf(action1)

    // Call onSessionCancelled with a CancellationException
    val cancellationException = kotlinx.coroutines.CancellationException("Test cancellation")
    monitor.onSessionCancelled(
      cause = cancellationException,
      droppedActions = droppedActions,
      session = rootSession
    )

    // Settle the runtime to flush updates
    monitor.onRuntimeUpdate(RuntimeSettled)

    // Verify dropped actions were logged even with cancellation exception
    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    val droppedLogLines = updates.filterIsInstance<ActionDroppedLogLine>()

    assertEquals(1, droppedLogLines.size)
    assertEquals("cancelledAction", droppedLogLines[0].actionName)
  }

  @Test
  fun `ActionDroppedLogLine formats correctly in log output`() {
    val actionName = "testAction"
    val logLine = ActionDroppedLogLine(actionName)
    val builder = StringBuilder()

    logLine.log(builder)

    val expected = "DROPPED: $actionName\n"
    assertEquals(expected, builder.toString())
  }

  @Test
  fun `onSessionCancelled with multiple actions logs all in order`() {
    val runtimeListener = TestWorkflowRuntimeLoopListener()
    val monitor = WorkflowRuntimeMonitor(
      runtimeName = runtimeName,
      runtimeLoopListener = runtimeListener
    )
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Initialize session
    monitor.onSessionStarted(testScope, rootSession)

    // Create multiple test actions with distinct names
    val actions = listOf(
      TestAction("firstAction"),
      TestAction("secondAction"),
      TestAction("thirdAction")
    )

    // Call onSessionCancelled with multiple dropped actions
    monitor.onSessionCancelled(
      cause = null,
      droppedActions = actions,
      session = rootSession
    )

    // Settle the runtime to flush updates
    monitor.onRuntimeUpdate(RuntimeSettled)

    // Verify all dropped actions were logged in order
    val updates = runtimeListener.runtimeUpdatesReceived!!.readAndClear()
    val droppedLogLines = updates.filterIsInstance<ActionDroppedLogLine>()

    assertEquals(3, droppedLogLines.size)
    assertEquals("firstAction", droppedLogLines[0].actionName)
    assertEquals("secondAction", droppedLogLines[1].actionName)
    assertEquals("thirdAction", droppedLogLines[2].actionName)
  }

  // Test helper classes
  private class TestWorkflow : StatefulWorkflow<String, String, String, String>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = props

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, String>
    ): String = renderState

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)

    fun createRootSession(context: CoroutineContext = EmptyCoroutineContext): WorkflowSession =
      TestWorkflowSession(
        workflow = this,
        sessionId = 1L,
        renderKey = "root",
        parent = null,
        runtimeContext = context
      )

    fun createChildSession(
      parent: WorkflowSession,
      context: CoroutineContext = EmptyCoroutineContext
    ): WorkflowSession = TestWorkflowSession(
      workflow = this,
      sessionId = 2L,
      renderKey = "child",
      parent = parent,
      runtimeContext = context
    )
  }

  private class TestWorkflowSession(
    private val workflow: TestWorkflow,
    override val sessionId: Long,
    override val renderKey: String,
    override val parent: WorkflowSession?,
    override val runtimeContext: CoroutineContext = EmptyCoroutineContext
  ) : WorkflowSession {
    override val identifier = workflow.identifier
    override val runtimeConfig = TestRuntimeConfig()
    override val workflowTracer = null
  }

  private class TestRuntimeConfig : RuntimeConfig {
    override fun contains(element: RuntimeConfigOptions): Boolean = false
    override val size: Int = 0
    override fun containsAll(elements: Collection<RuntimeConfigOptions>): Boolean = false
    override fun isEmpty(): Boolean = true
    override fun iterator(): Iterator<RuntimeConfigOptions> =
      emptyList<RuntimeConfigOptions>().iterator()

    override fun toString(): String = "TestRuntimeConfig"
  }

  private class TestBaseRenderContext : BaseRenderContext<String, String, String> {
    override val runtimeConfig = TestRuntimeConfig()
    override val workflowTracer = null
    override val actionSink: Sink<WorkflowAction<String, String, String>> = TestSink()

    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<String, String, String>
    ): ChildRenderingT {
      throw NotImplementedError("Not implemented for testing")
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    ) {
      throw NotImplementedError("Not implemented for testing")
    }

    override fun <ResultT> remember(
      key: String,
      resultType: KType,
      vararg inputs: Any?,
      calculation: () -> ResultT
    ): ResultT {
      throw NotImplementedError("Not implemented for testing")
    }
  }

  private class TestSink : Sink<WorkflowAction<String, String, String>> {
    override fun send(value: WorkflowAction<String, String, String>) {
      // No-op for testing
    }
  }

  private class TestAction(name: String) : WorkflowAction<String, String, String>() {
    override fun Updater.apply() {
      // No-op for testing
    }

    override val debuggingName: String = name
  }

  private class TestWorkflowRuntimeTracer : WorkflowRuntimeTracer() {
    var onWorkflowSessionStartedCalled = false
    var onWorkflowSessionStartedCallCount = 0
    var onWorkflowSessionStoppedCalled = false
    var onInitialStateCalled = false
    var onPropsChangedCalled = false
    var onRenderAndSnapshotCalled = false
    var onRenderCalled = false
    var onSnapshotStateWithChildrenCalled = false
    var onRuntimeUpdateEnhancedCalled = false
    var onRuntimeUpdateEnhancedCallCount = 0
    var onRootPropsChangedCalled = false

    override fun onWorkflowSessionStarted(
      workflowScope: CoroutineScope,
      session: WorkflowSession
    ) {
      onWorkflowSessionStartedCalled = true
      onWorkflowSessionStartedCallCount++
    }

    override fun onWorkflowSessionStopped(sessionId: Long) {
      onWorkflowSessionStoppedCalled = true
    }

    override fun <P, S> onInitialState(
      props: P,
      snapshot: Snapshot?,
      workflowScope: CoroutineScope,
      proceed: (P, Snapshot?, CoroutineScope) -> S,
      session: WorkflowSession
    ): S {
      onInitialStateCalled = true
      return proceed(props, snapshot, workflowScope)
    }

    override fun <P, S> onPropsChanged(
      old: P,
      new: P,
      state: S,
      proceed: (P, P, S) -> S,
      session: WorkflowSession
    ): S {
      onPropsChangedCalled = true
      return proceed(old, new, state)
    }

    override fun <P, R> onRenderAndSnapshot(
      renderProps: P,
      proceed: (P) -> RenderingAndSnapshot<R>,
      session: WorkflowSession
    ): RenderingAndSnapshot<R> {
      onRenderAndSnapshotCalled = true
      return proceed(renderProps)
    }

    override fun <P, S, O, R> onRender(
      renderProps: P,
      renderState: S,
      context: BaseRenderContext<P, S, O>,
      proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
      session: WorkflowSession
    ): R {
      onRenderCalled = true
      return proceed(renderProps, renderState, null)
    }

    override fun onSnapshotStateWithChildren(
      proceed: () -> TreeSnapshot,
      session: WorkflowSession
    ): TreeSnapshot {
      onSnapshotStateWithChildrenCalled = true
      return proceed()
    }

    override fun onRuntimeUpdateEnhanced(
      runtimeUpdate: RuntimeUpdate,
      currentActionHandlingChangedState: Boolean,
      configSnapshot: ConfigSnapshot
    ) {
      onRuntimeUpdateEnhancedCalled = true
      onRuntimeUpdateEnhancedCallCount++
    }

    override fun onRootPropsChanged(session: WorkflowSession) {
      onRootPropsChangedCalled = true
    }
  }

  private class TestWorkflowRenderPassTracker : WorkflowRenderPassTracker {
    var renderPassCount = 0
    var renderPassInfoReceived: RenderPassInfo? = null

    override fun recordRenderPass(renderPass: RenderPassInfo) {
      renderPassCount++
      renderPassInfoReceived = renderPass
    }
  }

  private class TestWorkflowRuntimeLoopListener : WorkflowRuntimeLoopListener {
    var onRuntimeLoopSettledCalled = false
    var runtimeUpdatesReceived: RuntimeUpdates? = null

    override fun onRuntimeLoopSettled(
      configSnapshot: ConfigSnapshot,
      runtimeUpdates: RuntimeUpdates
    ) {
      onRuntimeLoopSettledCalled = true
      runtimeUpdatesReceived = runtimeUpdates
    }
  }
}
