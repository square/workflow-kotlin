package com.squareup.workflow1.tracing.papa

import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.WorkflowInterceptor.RenderPassSkipped
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.identifier
import com.squareup.workflow1.tracing.ConfigSnapshot
import com.squareup.workflow1.tracing.RenderCause
import com.squareup.workflow1.tracing.RuntimeTraceContext
import com.squareup.workflow1.tracing.RuntimeUpdateLogLine
import com.squareup.workflow1.tracing.WorkflowSessionInfo
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class WorkflowPapaTracerTest {

  private val fakeTrace = FakeSafeTrace()
  private val papaTracer = WorkflowPapaTracer(fakeTrace)

  @Test
  fun `onWorkflowSessionStarted creates async section for root workflow`() {
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    // Add session info to the context as would normally be done by WorkflowRuntimeMonitor
    testContext.workflowSessionInfo[rootSession.sessionId] = WorkflowSessionInfo(rootSession)

    papaTracer.onWorkflowSessionStarted(testScope, rootSession)

    val asyncSectionCalls = fakeTrace.traceCalls.filter { it.type == "beginAsyncSection" }
    assertEquals(1, asyncSectionCalls.size)
    assertTrue(asyncSectionCalls.first().name!!.contains("WKF1"))
  }

  @Test
  fun `onInitialState traces section`() {
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()
    val testScope = TestScope()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    // Add session info to the context as would normally be done by WorkflowRuntimeMonitor
    testContext.workflowSessionInfo[rootSession.sessionId] = WorkflowSessionInfo(rootSession)

    val result = papaTracer.onInitialState(
      props = "testProps",
      snapshot = null,
      workflowScope = testScope,
      proceed = { _, _, _ -> "initialState" },
      session = rootSession
    )

    assertEquals("initialState", result)
    val beginSectionCalls = fakeTrace.traceCalls.filter { it.type == "beginSection" }
    val endSectionCalls = fakeTrace.traceCalls.filter { it.type == "endSection" }
    assertTrue(beginSectionCalls.any { it.label!!.contains("InitialState") })
    assertTrue(endSectionCalls.isNotEmpty())
  }

  @Test
  fun `onRenderAndSnapshot traces render section`() {
    val testWorkflow = TestWorkflow()
    val rootSession = testWorkflow.createRootSession()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    val result = papaTracer.onRenderAndSnapshot(
      renderProps = "props",
      proceed = { renderingAndSnapshot },
      session = rootSession
    )

    assertEquals(renderingAndSnapshot, result)
    val beginSectionCalls = fakeTrace.traceCalls.filter { it.type == "beginSection" }
    assertTrue(beginSectionCalls.any { it.label!!.contains("RENDER") })
  }

  @Test
  fun `tracer can be instantiated`() {
    assertNotNull(papaTracer)
  }

  @Test
  fun `onPropsChanged delegates to proceed function`() {
    val testWorkflow = TestWorkflow()
    val mockSession = testWorkflow.createMockSession()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    // Add session info to the context as would normally be done by WorkflowRuntimeMonitor
    testContext.workflowSessionInfo[mockSession.sessionId] = WorkflowSessionInfo(mockSession)

    val result = papaTracer.onPropsChanged(
      old = "old",
      new = "new",
      state = "current",
      proceed = { _, _, state -> state },
      session = mockSession
    )

    assertEquals("current", result)
  }

  @Test
  fun `onRenderAndSnapshot delegates to proceed function`() {
    val testWorkflow = TestWorkflow()
    val mockSession = testWorkflow.createMockSession()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    val expectedSnapshot = TreeSnapshot.forRootOnly(null)
    val renderingAndSnapshot = RenderingAndSnapshot("rendering", expectedSnapshot)

    val result = papaTracer.onRenderAndSnapshot(
      renderProps = "props",
      proceed = { renderingAndSnapshot },
      session = mockSession
    )

    assertEquals(renderingAndSnapshot, result)
  }

  @Test
  fun `onSnapshotStateWithChildren delegates to proceed function`() {
    val testWorkflow = TestWorkflow()
    val mockSession = testWorkflow.createMockSession()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    val treeSnapshot = TreeSnapshot.forRootOnly(null)

    val result = papaTracer.onSnapshotStateWithChildren(
      proceed = { treeSnapshot },
      session = mockSession
    )

    assertEquals(treeSnapshot, result)
  }

  @Test
  fun `onRuntimeUpdateEnhanced handles different runtime updates`() {
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    val configSnapshot = ConfigSnapshot(TestRuntimeConfig())

    // Should not throw for RenderPassSkipped
    papaTracer.onRuntimeUpdateEnhanced(RenderPassSkipped, false, configSnapshot)

    // Should not throw for RuntimeLoopSettled
    papaTracer.onRuntimeUpdateEnhanced(RuntimeSettled, true, configSnapshot)
  }

  @Test
  fun `workflow sessions can be started and stopped`() {
    val testWorkflow = TestWorkflow()
    val mockSession = testWorkflow.createMockSession()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    // Add session info to the context as would normally be done by WorkflowRuntimeMonitor
    testContext.workflowSessionInfo[mockSession.sessionId] = WorkflowSessionInfo(mockSession)

    // Should not throw
    papaTracer.onWorkflowSessionStarted(TestScope(), mockSession)
    papaTracer.onWorkflowSessionStopped(123L)
  }

  @Test
  fun `onRootPropsChanged completes without error`() {
    val testWorkflow = TestWorkflow()
    val mockSession = testWorkflow.createMockSession()

    // Attach runtime context to tracer
    val testContext = TestRuntimeTraceContext()
    papaTracer.attachRuntimeContext(testContext)

    // Add session info to the context as would normally be done by WorkflowRuntimeMonitor
    testContext.workflowSessionInfo[mockSession.sessionId] = WorkflowSessionInfo(mockSession)

    // Should not throw
    papaTracer.onRootPropsChanged(mockSession)
  }

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

    fun createRootSession(): WorkflowSession = TestWorkflowSession(
      workflow = this,
      sessionId = 1L,
      renderKey = "root",
      parent = null
    )

    fun createMockSession() = TestWorkflowSession(
      workflow = this,
      sessionId = 123L,
      renderKey = "test",
      parent = null
    )
  }

  private class TestWorkflowSession(
    private val workflow: TestWorkflow,
    override val sessionId: Long,
    override val renderKey: String,
    override val parent: WorkflowSession?
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

  private class TestRuntimeTraceContext : RuntimeTraceContext {
    override val runtimeName: String = "TestRuntime"
    override val workflowSessionInfo =
      androidx.collection.mutableLongObjectMapOf<WorkflowSessionInfo>()
    override val renderIncomingCauses: MutableList<RenderCause> = mutableListOf()
    override var previousRenderCause: RenderCause? = null
    override var currentRenderCause: RenderCause? = null
    override lateinit var configSnapshot: ConfigSnapshot

    override fun addRuntimeUpdate(event: RuntimeUpdateLogLine) {
      // No-op for testing
    }

    init {
      configSnapshot = ConfigSnapshot(TestRuntimeConfig())
    }
  }
}
