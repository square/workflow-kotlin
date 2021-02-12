@file:Suppress("EXPERIMENTAL_API_USAGE", "DEPRECATION")

package com.squareup.workflow1.internal

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.emitOutput
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.action
import com.squareup.workflow1.contraMap
import com.squareup.workflow1.identifier
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.rendering
import com.squareup.workflow1.stateful
import com.squareup.workflow1.stateless
import com.squareup.workflow1.writeUtf8WithLength
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalWorkflowApi::class)
class WorkflowNodeTest {

  private abstract class StringWorkflow : StatefulWorkflow<String, String, String, String>() {
    override fun snapshotState(state: String): Snapshot = fail("not expected")
  }

  private abstract class StringEventWorkflow :
      StatefulWorkflow<String, String, String, (String) -> Unit>() {
    override fun snapshotState(state: String): Snapshot = fail("not expected")
  }

  private class PropsRenderingWorkflow(
    private val onPropsChanged: (String, String, String) -> String
  ) : StringWorkflow() {

    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String {
      assertNull(snapshot)
      return "starting:$props"
    }

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = onPropsChanged.invoke(old, new, state)

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext
    ): String {
      return """
        props:$renderProps
        state:$renderState
      """.trimIndent()
    }
  }

  private val context: CoroutineContext = Unconfined + Job()

  @AfterTest fun tearDown() {
    context.cancel()
  }

  @Test fun `onPropsChanged is called when props change`() {
    val oldAndNewProps = mutableListOf<Pair<String, String>>()
    val workflow = PropsRenderingWorkflow { old, new, state ->
      oldAndNewProps += old to new
      return@PropsRenderingWorkflow state
    }
    val node = WorkflowNode(workflow.id(), workflow, "old", null, context)

    node.render(workflow, "new")

    assertEquals(listOf("old" to "new"), oldAndNewProps)
  }

  @Test fun `onPropsChanged is not called when props are equal`() {
    val oldAndNewProps = mutableListOf<Pair<String, String>>()
    val workflow = PropsRenderingWorkflow { old, new, state ->
      oldAndNewProps += old to new
      return@PropsRenderingWorkflow state
    }
    val node = WorkflowNode(workflow.id(), workflow, "old", null, context)

    node.render(workflow, "old")

    assertTrue(oldAndNewProps.isEmpty())
  }

  @Test fun `props are rendered`() {
    val workflow = PropsRenderingWorkflow { old, new, _ ->
      "$old->$new"
    }
    val node = WorkflowNode(workflow.id(), workflow, "foo", null, context)

    val rendering = node.render(workflow, "foo2")

    assertEquals(
        """
          props:foo2
          state:foo->foo2
        """.trimIndent(), rendering
    )

    val rendering2 = node.render(workflow, "foo3")

    assertEquals(
        """
          props:foo3
          state:foo2->foo3
        """.trimIndent(), rendering2
    )
  }

  @Test fun `accepts event`() {
    val workflow = object : StringEventWorkflow() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): String {
        assertNull(snapshot)
        return props
      }

      override fun render(
        renderProps: String,
        renderState: String,
        context: RenderContext
      ): (String) -> Unit {
        return context.eventHandler { event -> setOutput(event) }
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow, "", null, context,
        emitOutputToParent = { WorkflowOutput("tick:$it") }
    )
    node.render(workflow, "")("event")

    val result = runBlocking {
      withTimeout(10) {
        select<WorkflowOutput<String>?> {
          node.tick(this)
        }
      }
    }
    assertEquals("tick:event", result?.value)
  }

  @Test fun `accepts events sent to stale renderings`() {
    val workflow = object : StringEventWorkflow() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): String {
        assertNull(snapshot)
        return props
      }

      override fun render(
        renderProps: String,
        renderState: String,
        context: RenderContext
      ): (String) -> Unit {
        return context.eventHandler { event -> setOutput(event) }
      }
    }
    val node = WorkflowNode(workflow.id(), workflow, "", null, context,
        emitOutputToParent = { WorkflowOutput("tick:$it") }
    )
    val sink = node.render(workflow, "")

    sink("event")
    sink("event2")

    val result = runBlocking {
      withTimeout(10) {
        List(2) {
          select<WorkflowOutput<String>?> {
            node.tick(this)
          }
        }
      }
    }
    assertEquals(listOf("tick:event", "tick:event2"), result.map { it?.value })
  }

  @Test fun `send allows subsequent events on same rendering`() {
    lateinit var sink: Sink<WorkflowAction<String, String, String>>
    val workflow = object : StringWorkflow() {
      override fun initialState(
        props: String,
        snapshot: Snapshot?
      ): String {
        assertNull(snapshot)
        return props
      }

      override fun render(
        renderProps: String,
        renderState: String,
        context: RenderContext
      ): String {
        sink = context.actionSink
        return ""
      }
    }
    val node = WorkflowNode(workflow.id(), workflow, "", null, context)

    node.render(workflow, "")
    sink.send(emitOutput("event"))

    // Should not throw.
    sink.send(emitOutput("event2"))
  }

  @Test fun `sideEffect is not started until after render completes`() {
    var started = false
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("key") {
        started = true
      }
      assertFalse(started)
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = Unit,
        snapshot = null, baseContext = context
    )

    runBlocking {
      node.render(workflow.asStatefulWorkflow(), Unit)
      assertTrue(started)
    }
  }

  @Test fun `sideEffect coroutine is named`() {
    var contextFromWorker: CoroutineContext? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("the key") {
        contextFromWorker = coroutineContext
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = Unit,
        snapshot = null, baseContext = context
    )

    node.render(workflow.asStatefulWorkflow(), Unit)
    assertEquals(WorkflowNodeId(workflow).toString(), node.coroutineContext[CoroutineName]!!.name)
    assertEquals(
        "sideEffect[the key] for ${WorkflowNodeId(workflow)}",
        contextFromWorker!![CoroutineName]!!.name
    )
  }

  @Test fun `sideEffect can send to actionSink`() {
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningSideEffect("key") {
        actionSink.send(action { setOutput("result") })
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = Unit,
        snapshot = null, baseContext = context
    )
    node.render(workflow.asStatefulWorkflow(), Unit)

    val result = runBlocking {
      // Result should be available instantly, any delay at all indicates something is broken.
      withTimeout(1) {
        select<WorkflowOutput<String>?> {
          node.tick(this)
        }
      }
    }

    assertEquals("result", result?.value)
  }

  @Test fun `sideEffect is cancelled when stops being ran`() {
    val isRunning = MutableStateFlow(true)
    var cancellationException: Throwable? = null
    val workflow = Workflow.stateless<Boolean, Nothing, Unit> { props ->
      if (props) {
        runningSideEffect("key") {
          suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cause -> cancellationException = cause }
          }
        }
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = true,
        snapshot = null, baseContext = context
    )

    runBlocking {
      node.render(workflow.asStatefulWorkflow(), true)
      assertNull(cancellationException)

      // Stop running the side effect.
      isRunning.value = false
      node.render(workflow.asStatefulWorkflow(), false)

      assertTrue(cancellationException is CancellationException)
    }
  }

  @Test fun `sideEffect is cancelled when workflow is torn down`() {
    var cancellationException: Throwable? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("key") {
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { cause -> cancellationException = cause }
        }
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = Unit,
        snapshot = null, baseContext = context
    )

    runBlocking {
      node.render(workflow.asStatefulWorkflow(), Unit)
      assertNull(cancellationException)

      node.cancel()

      assertTrue(cancellationException is CancellationException)
    }
  }

  @Test fun `sideEffect with matching key lives across render passes`() {
    var renderPasses = 0
    var cancelled = false
    val workflow = Workflow.stateless<Int, Nothing, Unit> {
      renderPasses++
      runningSideEffect("") {
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { cancelled = true }
        }
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = 0,
        snapshot = null, baseContext = context
    )

    runBlocking {
      node.render(workflow.asStatefulWorkflow(), 0)
      assertFalse(cancelled)
      assertEquals(1, renderPasses)

      node.render(workflow.asStatefulWorkflow(), 1)
      assertFalse(cancelled)
      assertEquals(2, renderPasses)
    }
  }

  @Test fun `sideEffect isn't restarted on next render pass after finishing`() {
    val seenProps = mutableListOf<Int>()
    var renderPasses = 0
    val workflow = Workflow.stateless<Int, Nothing, Unit> { props ->
      renderPasses++
      runningSideEffect("") {
        seenProps += props
      }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = 0,
        snapshot = null, baseContext = context
    )

    runBlocking {
      node.render(workflow.asStatefulWorkflow(), 0)
      assertEquals(listOf(0), seenProps)
      assertEquals(1, renderPasses)

      node.render(workflow.asStatefulWorkflow(), 1)
      assertEquals(listOf(0), seenProps)
      assertEquals(2, renderPasses)
    }
  }

  @Test fun `multiple sideEffects with same key throws`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("same") { fail() }
      runningSideEffect("same") { fail() }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = Unit,
        snapshot = null, baseContext = context
    )

    val error = assertFailsWith<IllegalArgumentException> {
      node.render(workflow.asStatefulWorkflow(), Unit)
    }
    assertEquals("Expected side effect keys to be unique: \"same\"", error.message)
  }

  @Test fun `staggered sideEffects`() {
    val events1 = mutableListOf<String>()
    val events2 = mutableListOf<String>()
    val events3 = mutableListOf<String>()
    fun recordingSideEffect(events: MutableList<String>): suspend CoroutineScope.() -> Unit = {
      events += "started"
      suspendCancellableCoroutine<Nothing> { continuation ->
        continuation.invokeOnCancellation { events += "cancelled" }
      }
    }

    val workflow = Workflow.stateless<Int, Nothing, Unit> { props ->
      if (props in 0..2) runningSideEffect("one", recordingSideEffect(events1))
      if (props == 1) runningSideEffect("two", recordingSideEffect(events2))
      if (props == 2) runningSideEffect("three", recordingSideEffect(events3))
    }
        .asStatefulWorkflow()
    val node = WorkflowNode(
        workflow.id(), workflow, initialProps = 0, snapshot = null,
        baseContext = context
    )

    node.render(workflow, 0)
    assertEquals(listOf("started"), events1)
    assertEquals(emptyList<String>(), events2)
    assertEquals(emptyList<String>(), events3)

    node.render(workflow, 1)
    assertEquals(listOf("started"), events1)
    assertEquals(listOf("started"), events2)
    assertEquals(emptyList<String>(), events3)

    node.render(workflow, 2)
    assertEquals(listOf("started"), events1)
    assertEquals(listOf("started", "cancelled"), events2)
    assertEquals(listOf("started"), events3)

    node.render(workflow, 3)
    assertEquals(listOf("started", "cancelled"), events1)
    assertEquals(listOf("started", "cancelled"), events2)
    assertEquals(listOf("started", "cancelled"), events3)
  }

  @Test fun `multiple sideEffects started in same pass are both launched`() {
    var started1 = false
    var started2 = false
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("one") { started1 = true }
      runningSideEffect("two") { started2 = true }
    }
    val node = WorkflowNode(
        workflow.id(), workflow.asStatefulWorkflow(), initialProps = Unit,
        snapshot = null, baseContext = context
    )

    assertFalse(started1)
    assertFalse(started2)
    node.render(workflow.asStatefulWorkflow(), Unit)
    assertTrue(started1)
    assertTrue(started2)
  }

  @Test fun `snapshots non-empty without children`() {
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props, snapshot ->
          snapshot?.bytes?.parse {
            it.readUtf8WithLength()
                .removePrefix("state:")
          } ?: props
        },
        render = { _, state -> state },
        snapshot = { state ->
          Snapshot.write {
            it.writeUtf8WithLength("state:$state")
          }
        }
    )
    val originalNode = WorkflowNode(
        workflow.id(),
        workflow,
        initialProps = "initial props",
        snapshot = null,
        baseContext = Unconfined
    )

    assertEquals("initial props", originalNode.render(workflow, "foo"))
    val snapshot = originalNode.snapshot(workflow)
    assertNotEquals(0, snapshot.toByteString().size)

    val restoredNode = WorkflowNode(
        workflow.id(),
        workflow,
        // These props should be ignored, since snapshot is non-null.
        initialProps = "new props",
        snapshot = snapshot,
        baseContext = Unconfined
    )
    assertEquals("initial props", restoredNode.render(workflow, "foo"))
  }

  @Test fun `snapshots empty without children`() {
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props, snapshot -> snapshot?.bytes?.utf8() ?: props },
        render = { _, state -> state },
        snapshot = { Snapshot.of("restored") }
    )
    val originalNode = WorkflowNode(
        workflow.id(),
        workflow,
        initialProps = "initial props",
        snapshot = null,
        baseContext = Unconfined
    )

    assertEquals("initial props", originalNode.render(workflow, "foo"))
    val snapshot = originalNode.snapshot(workflow)
    assertNotEquals(0, snapshot.toByteString().size)

    val restoredNode = WorkflowNode(
        workflow.id(),
        workflow,
        // These props should be ignored, since snapshot is non-null.
        initialProps = "new props",
        snapshot = snapshot,
        baseContext = Unconfined
    )
    assertEquals("restored", restoredNode.render(workflow, "foo"))
  }

  @Test fun `snapshots non-empty with children`() {
    var restoredChildState: String? = null
    var restoredParentState: String? = null
    val childWorkflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props, snapshot ->
          snapshot?.bytes?.parse {
            it.readUtf8WithLength()
                .removePrefix("child state:")
                .also { state -> restoredChildState = state }
          } ?: props
        },
        render = { _, state -> state },
        snapshot = { state ->
          Snapshot.write {
            it.writeUtf8WithLength("child state:$state")
          }
        }
    )
    val parentWorkflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props, snapshot ->
          snapshot?.bytes?.parse {
            it.readUtf8WithLength()
                .removePrefix("parent state:")
                .also { state -> restoredParentState = state }
          } ?: props
        },
        render = { _, state -> "$state|" + renderChild(childWorkflow, "child props") },
        snapshot = { state ->
          Snapshot.write {
            it.writeUtf8WithLength("parent state:$state")
          }
        }
    )

    val originalNode = WorkflowNode(
        parentWorkflow.id(),
        parentWorkflow,
        initialProps = "initial props",
        snapshot = null,
        baseContext = Unconfined
    )

    assertEquals("initial props|child props", originalNode.render(parentWorkflow, "foo"))
    val snapshot = originalNode.snapshot(parentWorkflow)
    assertNotEquals(0, snapshot.toByteString().size)

    val restoredNode = WorkflowNode(
        parentWorkflow.id(),
        parentWorkflow,
        // These props should be ignored, since snapshot is non-null.
        initialProps = "new props",
        snapshot = snapshot,
        baseContext = Unconfined
    )
    assertEquals("initial props|child props", restoredNode.render(parentWorkflow, "foo"))
    assertEquals("child props", restoredChildState)
    assertEquals("initial props", restoredParentState)
  }

  @Test fun `snapshot counts`() {
    var snapshotCalls = 0
    var restoreCalls = 0
    // Track the number of times the snapshot is actually serialized, not snapshotState is called.
    var snapshotWrites = 0
    val workflow = Workflow.stateful<Unit, Nothing, Unit>(
        initialState = { snapshot -> if (snapshot != null) restoreCalls++ },
        render = { Unit },
        snapshot = {
          snapshotCalls++
          Snapshot.write {
            snapshotWrites++
            // Snapshot will be discarded on restoration if it's empty, so we need to write
            // something here so we actually get a non-null snapshot in restore.
            it.writeUtf8("dummy value")
          }
        }
    )
    val node = WorkflowNode(workflow.id(), workflow, Unit, null, Unconfined)

    assertEquals(0, snapshotCalls)
    assertEquals(0, snapshotWrites)
    assertEquals(0, restoreCalls)

    val snapshot = node.snapshot(workflow)

    assertEquals(1, snapshotCalls)
    assertEquals(0, snapshotWrites)
    assertEquals(0, restoreCalls)

    snapshot.toByteString()

    assertEquals(1, snapshotCalls)
    assertEquals(1, snapshotWrites)
    assertEquals(0, restoreCalls)

    WorkflowNode(workflow.id(), workflow, Unit, snapshot, Unconfined)

    assertEquals(1, snapshotCalls)
    assertEquals(1, snapshotWrites)
    assertEquals(1, restoreCalls)
  }

  @Test fun `restore gets props`() {
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props, snapshot ->
          snapshot?.bytes?.parse {
            // Tags the restored state with the props so we can check it.
            val deserialized = it.readUtf8WithLength()
            return@parse "props:$props|state:$deserialized"
          } ?: props
        },
        render = { _, state -> state },
        snapshot = { state -> Snapshot.write { it.writeUtf8WithLength(state) } }
    )
    val originalNode = WorkflowNode(
        workflow.id(),
        workflow,
        initialProps = "initial props",
        snapshot = null,
        baseContext = Unconfined
    )

    assertEquals("initial props", originalNode.render(workflow, "foo"))
    val snapshot = originalNode.snapshot(workflow)
    assertNotEquals(0, snapshot.toByteString().size)

    val restoredNode = WorkflowNode(
        workflow.id(),
        workflow,
        initialProps = "new props",
        snapshot = snapshot,
        baseContext = Unconfined
    )
    assertEquals("props:new props|state:initial props", restoredNode.render(workflow, "foo"))
  }

  @Test fun `toString() formats as WorkflowInstance without parent`() {
    val workflow = Workflow.rendering(Unit)
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined,
        parent = null
    )

    assertEquals(
        "WorkflowInstance(identifier=${workflow.identifier}, renderKey=foo, " +
          "instanceId=0, parent=null)",
        node.toString()
    )
  }

  @Test fun `toString() formats as WorkflowInstance with parent`() {
    val workflow = Workflow.rendering(Unit)
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined,
        parent = TestSession(42)
    )

    assertEquals(
        "WorkflowInstance(identifier=${workflow.identifier}, renderKey=foo, " +
          "instanceId=0, parent=WorkflowInstance(…))",
        node.toString()
    )
  }

  @Test fun `interceptor handles scope start and cancellation`() {
    lateinit var interceptedScope: CoroutineScope
    lateinit var interceptedSession: WorkflowSession
    lateinit var cancellationException: Throwable
    val interceptor = object : WorkflowInterceptor {
      override fun onSessionStarted(
        workflowScope: CoroutineScope,
        session: WorkflowSession
      ) {
        interceptedScope = workflowScope
        interceptedSession = session
        workflowScope.coroutineContext[Job]!!.invokeOnCompletion {
          cancellationException = it!!
        }
      }
    }
    val workflow = Workflow.rendering(Unit)
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42)
    )

    assertSame(node.coroutineContext, interceptedScope.coroutineContext)
    assertEquals(workflow.identifier, interceptedSession.identifier)
    assertEquals(0, interceptedSession.sessionId)
    assertEquals("foo", interceptedSession.renderKey)
    assertEquals(42, interceptedSession.parent!!.sessionId)

    val cause = CancellationException("stop")
    node.cancel(cause)
    assertSame(cause, cancellationException)
  }

  @Test fun `interceptor handles initialState()`() {
    lateinit var interceptedProps: String
    lateinit var interceptedSnapshot: Snapshot
    lateinit var interceptedState: String
    lateinit var interceptedSession: WorkflowSession
    val interceptor = object : WorkflowInterceptor {
      override fun <P, S> onInitialState(
        props: P,
        snapshot: Snapshot?,
        proceed: (P, Snapshot?) -> S,
        session: WorkflowSession
      ): S {
        interceptedProps = props as String
        interceptedSnapshot = snapshot!!
        interceptedSession = session
        return proceed(props, snapshot)
            .also { interceptedState = it as String }
      }
    }
    val workflow = Workflow.stateful<String, String, Nothing, Unit>(
        initialState = { props -> "state($props)" },
        render = { _, _ -> fail() }
    )
    WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = "props",
        snapshot = TreeSnapshot.forRootOnly(Snapshot.of("snapshot")),
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42)
    )

    assertEquals("props", interceptedProps)
    assertEquals(Snapshot.of("snapshot"), interceptedSnapshot)
    assertEquals("state(props)", interceptedState)
    assertEquals(workflow.identifier, interceptedSession.identifier)
    assertEquals(0, interceptedSession.sessionId)
    assertEquals("foo", interceptedSession.renderKey)
    assertEquals(42, interceptedSession.parent!!.sessionId)
  }

  @Test fun `interceptor handles onPropsChanged()`() {
    lateinit var interceptedOld: String
    lateinit var interceptedNew: String
    lateinit var interceptedState: String
    lateinit var interceptedReturnState: String
    lateinit var interceptedSession: WorkflowSession
    val interceptor = object : WorkflowInterceptor {
      override fun <P, S> onPropsChanged(
        old: P,
        new: P,
        state: S,
        proceed: (P, P, S) -> S,
        session: WorkflowSession
      ): S {
        interceptedOld = old as String
        interceptedNew = new as String
        interceptedState = state as String
        interceptedSession = session
        return proceed(old, new, state)
            .also { interceptedReturnState = it as String }
      }
    }
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { "initialState" },
        onPropsChanged = { old, new, state -> "onPropsChanged($old, $new, $state)" },
        render = { _, state -> state }
    )
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = "old",
        snapshot = null,
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42)
    )
    val rendering = node.render(workflow, "new")

    assertEquals("old", interceptedOld)
    assertEquals("new", interceptedNew)
    assertEquals("initialState", interceptedState)
    assertEquals("onPropsChanged(old, new, initialState)", interceptedReturnState)
    assertEquals("onPropsChanged(old, new, initialState)", rendering)
    assertEquals(workflow.identifier, interceptedSession.identifier)
    assertEquals(0, interceptedSession.sessionId)
    assertEquals("foo", interceptedSession.renderKey)
    assertEquals(42, interceptedSession.parent!!.sessionId)
  }

  @Test fun `interceptor handles render()`() {
    lateinit var interceptedProps: String
    lateinit var interceptedState: String
    lateinit var interceptedContext: BaseRenderContext<*, *, *>
    lateinit var interceptedRendering: String
    lateinit var interceptedSession: WorkflowSession
    val interceptor = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
        session: WorkflowSession
      ): R {
        interceptedProps = renderProps as String
        interceptedState = renderState as String
        interceptedContext = context
        interceptedSession = session
        return proceed(renderProps, renderState, context)
            .also { interceptedRendering = it as String }
      }
    }
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { "state" },
        render = { props, state -> "render($props, $state)" }
    )
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = "props",
        snapshot = null,
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42)
    )
    val rendering = node.render(workflow, "props")

    assertEquals("props", interceptedProps)
    assertEquals("state", interceptedState)
    assertNotNull(interceptedContext)
    assertEquals("render(props, state)", interceptedRendering)
    assertEquals("render(props, state)", rendering)
    assertEquals(workflow.identifier, interceptedSession.identifier)
    assertEquals(0, interceptedSession.sessionId)
    assertEquals("foo", interceptedSession.renderKey)
    assertEquals(42, interceptedSession.parent!!.sessionId)
  }

  @Test fun `interceptor handles snapshotState()`() {
    lateinit var interceptedState: String
    var interceptedSnapshot: Snapshot? = null
    lateinit var interceptedSession: WorkflowSession
    val interceptor = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot?,
        session: WorkflowSession
      ): Snapshot? {
        interceptedState = state as String
        interceptedSession = session
        return proceed(state)
            .also { interceptedSnapshot = it }
      }
    }
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { _, _ -> "state" },
        render = { _, state -> state },
        snapshot = { state -> Snapshot.of("snapshot($state)") }
    )
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = "old",
        snapshot = null,
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42)
    )
    val snapshot = node.snapshot(workflow)

    assertEquals("state", interceptedState)
    assertEquals(Snapshot.of("snapshot(state)"), interceptedSnapshot)
    assertEquals(Snapshot.of("snapshot(state)"), snapshot.workflowSnapshot)
    assertEquals(workflow.identifier, interceptedSession.identifier)
    assertEquals(0, interceptedSession.sessionId)
    assertEquals("foo", interceptedSession.renderKey)
    assertEquals(42, interceptedSession.parent!!.sessionId)
  }

  @Test fun `interceptor handles snapshotState() returning null`() {
    lateinit var interceptedState: String
    var interceptedSnapshot: Snapshot? = null
    lateinit var interceptedSession: WorkflowSession
    val interceptor = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot?,
        session: WorkflowSession
      ): Snapshot? {
        interceptedState = state as String
        interceptedSession = session
        return proceed(state)
            .also { interceptedSnapshot = it }
      }
    }
    val workflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { _, _ -> "state" },
        render = { _, state -> state },
        snapshot = { null }
    )
    val node = WorkflowNode(
        id = workflow.id(key = "foo"),
        workflow = workflow.asStatefulWorkflow(),
        initialProps = "old",
        snapshot = null,
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42)
    )
    val snapshot = node.snapshot(workflow)

    assertEquals("state", interceptedState)
    assertNull(interceptedSnapshot)
    assertNull(snapshot.workflowSnapshot)
    assertEquals(workflow.identifier, interceptedSession.identifier)
    assertEquals(0, interceptedSession.sessionId)
    assertEquals("foo", interceptedSession.renderKey)
    assertEquals(42, interceptedSession.parent!!.sessionId)
  }

  @Test fun `interceptor is propagated to children`() {
    val interceptor = object : WorkflowInterceptor {
      @Suppress("UNCHECKED_CAST")
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
        session: WorkflowSession
      ): R = "[${proceed("[$renderProps]" as P, "[$renderState]" as S, context)}]" as R
    }
    val leafWorkflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props -> props },
        render = { props, state -> "leaf($props, $state)" }
    )
    val rootWorkflow = Workflow.stateful<String, String, Nothing, String>(
        initialState = { props -> props },
        render = { props, _ ->
          "root(${renderChild(leafWorkflow, props)})"
        }
    )
    val node = WorkflowNode(
        id = rootWorkflow.id(key = "foo"),
        workflow = rootWorkflow.asStatefulWorkflow(),
        initialProps = "props",
        snapshot = null,
        interceptor = interceptor,
        baseContext = Unconfined,
        parent = TestSession(42),
        idCounter = IdCounter()
    )
    val rendering = node.render(rootWorkflow.asStatefulWorkflow(), "props")

    assertEquals("[root([leaf([[props]], [[props]])])]", rendering)
  }

  @Test fun `eventSink send fails before render pass completed`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      val sink = eventHandler { _: String -> fail("Expected handler to fail.") }
      sink("Foo")
    }
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined
    )

    val error = assertFailsWith<UnsupportedOperationException> {
      node.render(workflow.asStatefulWorkflow(), Unit)
    }
    assertTrue(
        error.message!!.startsWith(
            "Expected sink to not be sent to until after the render pass. " +
                "Received action: WorkflowAction(eventHandler)@"
        )
    )
  }

  @Test fun `send fails before render pass completed`() {
    class TestAction : WorkflowAction<Unit, Nothing, Nothing>() {
      override fun Updater.apply() = fail("Expected sink send to fail.")
      override fun toString(): String = "TestAction()"
    }

    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      actionSink.send(TestAction())
    }
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined
    )

    val error = assertFailsWith<UnsupportedOperationException> {
      node.render(workflow.asStatefulWorkflow(), Unit)
    }
    assertEquals(
        "Expected sink to not be sent to until after the render pass. " +
            "Received action: TestAction()",
        error.message
    )
  }

  @Test fun `actionSink action changes state`() {
    val workflow = Workflow.stateful<Unit, String, Nothing, Pair<String, Sink<String>>>(
        initialState = { "initial" },
        render = { _, renderState ->
          renderState to actionSink.contraMap {
            action { state = "$state->$it" }
          }
        }
    )
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined
    )
    val (_, sink) = node.render(workflow.asStatefulWorkflow(), Unit)

    sink.send("hello")

    runBlocking {
      select<WorkflowOutput<String>?> {
        node.tick(this)
      }
    }

    val (state, _) = node.render(workflow.asStatefulWorkflow(), Unit)
    assertEquals("initial->hello", state)
  }

  @Test fun `actionSink action emits output`() {
    val workflow = Workflow.stateless<Unit, String, Sink<String>> {
      actionSink.contraMap { action { setOutput(it) } }
    }
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined,
        emitOutputToParent = { WorkflowOutput("output:$it") }
    )
    val rendering = node.render(workflow.asStatefulWorkflow(), Unit)

    rendering.send("hello")

    val output = runBlocking {
      select<WorkflowOutput<String>?> {
        node.tick(this)
      }
    }

    assertEquals("output:hello", output?.value)
  }

  @Test fun `actionSink action allows null output`() {
    val workflow = Workflow.stateless<Unit, String?, Sink<String?>> {
      actionSink.contraMap { action { setOutput(null) } }
    }
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined,
        emitOutputToParent = { WorkflowOutput(it) }
    )
    val rendering = node.render(workflow.asStatefulWorkflow(), Unit)

    rendering.send("hello")

    val output = runBlocking {
      select<WorkflowOutput<String>?> {
        node.tick(this)
      }
    }

    assertNull(output?.value)
  }

  @Test fun `child action changes state`() {
    val workflow = Workflow.stateful<Unit, String, Nothing, String>(
        initialState = { "initial" },
        render = { _, renderState ->
          runningSideEffect("test") {
            actionSink.send(action { state = "$state->hello" })
          }
          return@stateful renderState
        }
    )
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined
    )
    node.render(workflow.asStatefulWorkflow(), Unit)

    runBlocking {
      select<WorkflowOutput<String>?> {
        node.tick(this)
      }
    }

    val state = node.render(workflow.asStatefulWorkflow(), Unit)
    assertEquals("initial->hello", state)
  }

  @Test fun `child action emits output`() {
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningSideEffect("test") {
        actionSink.send(action { setOutput("child:hello") })
      }
    }
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined,
        emitOutputToParent = { WorkflowOutput("output:$it") }
    )
    node.render(workflow.asStatefulWorkflow(), Unit)

    val output = runBlocking {
      select<WorkflowOutput<String>?> {
        node.tick(this)
      }
    }

    assertEquals("output:child:hello", output?.value)
  }

  @Test fun `child action allows null output`() {
    val workflow = Workflow.stateless<Unit, String?, Unit> {
      runningSideEffect("test") {
        actionSink.send(action { setOutput(null) })
      }
    }
    val node = WorkflowNode(
        workflow.id(),
        workflow.asStatefulWorkflow(),
        initialProps = Unit,
        snapshot = null,
        baseContext = Unconfined,
        emitOutputToParent = { WorkflowOutput(it) }
    )
    node.render(workflow.asStatefulWorkflow(), Unit)

    val output = runBlocking {
      select<WorkflowOutput<String>?> {
        node.tick(this)
      }
    }

    assertNull(output?.value)
  }

  private class TestSession(override val sessionId: Long = 0) : WorkflowSession {
    override val identifier: WorkflowIdentifier = Workflow.rendering(Unit).identifier
    override val renderKey: String = ""
    override val parent: WorkflowSession? = null
  }
}
