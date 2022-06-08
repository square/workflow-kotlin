package com.squareup.workflow1

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class RenderWorkflowInTest {

  /**
   * A [TestScope] that will not run until explicitly told to.
   */
  private val pausedTestScope = TestScope()

  /**
   * A [TestScope] that will automatically dispatch enqueued routines.
   */
  private val testScope = TestScope(UnconfinedTestDispatcher())

  @Test fun `initial rendering is calculated synchronously`() {
    val props = MutableStateFlow("foo")
    val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
    // Don't allow the workflow runtime to actually start.

    val renderings = renderWorkflowIn(workflow, pausedTestScope, props) {}
    assertEquals("props: foo", renderings.value.rendering)
  }

  @Test fun `initial rendering is calculated when scope cancelled before start`() {
    val props = MutableStateFlow("foo")
    val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }

    pausedTestScope.cancel()
    val renderings = renderWorkflowIn(workflow, pausedTestScope, props) {}
    assertEquals("props: foo", renderings.value.rendering)
  }

  @Test
  // ktlint-disable max-line-length
  fun `side effects from initial rendering in root workflow are never started when scope cancelled before start`() { // ktlint-disable max-line-length
    var sideEffectWasRan = false
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        sideEffectWasRan = true
      }
    }

    testScope.cancel()
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    testScope.advanceUntilIdle()

    assertFalse(sideEffectWasRan)
  }

  @Test
  fun `side effects from initial rendering in non-root workflow are never started when scope cancelled before start`() { // ktlint-disable max-line-length
    var sideEffectWasRan = false
    val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        sideEffectWasRan = true
      }
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(childWorkflow)
    }

    testScope.cancel()
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    testScope.advanceUntilIdle()

    assertFalse(sideEffectWasRan)
  }

  @Test fun `new renderings are emitted on update`() {
    val props = MutableStateFlow("foo")
    val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
    val renderings = renderWorkflowIn(workflow, testScope, props) {}

    assertEquals("props: foo", renderings.value.rendering)

    props.value = "bar"

    assertEquals("props: bar", renderings.value.rendering)
  }

  @Test fun `saves to and restores from snapshot`() {
    val workflow = Workflow.stateful<Unit, String, Nothing, Pair<String, (String) -> Unit>>(
      initialState = { _, snapshot ->
        snapshot?.bytes?.parse { it.readUtf8WithLength() } ?: "initial state"
      },
      snapshot = { state ->
        Snapshot.write { it.writeUtf8WithLength(state) }
      },
      render = { _, renderState ->
        Pair(
          renderState,
          { newState -> actionSink.send(action { state = newState }) }
        )
      }
    )
    val props = MutableStateFlow(Unit)
    val renderings = renderWorkflowIn(workflow, testScope, props) {}

    // Interact with the workflow to change the state.
    renderings.value.rendering.let { (state, updateState) ->
      assertEquals("initial state", state)
      updateState("updated state")
    }

    val snapshot = renderings.value.let { (rendering, snapshot) ->
      val (state, updateState) = rendering
      assertEquals("updated state", state)
      updateState("ignored rendering")
      return@let snapshot
    }

    // Create a new scope to launch a second runtime to restore.
    val restoreScope = TestScope()
    val restoredRenderings =
      renderWorkflowIn(workflow, restoreScope, props, initialSnapshot = snapshot) {}
    assertEquals("updated state", restoredRenderings.value.rendering.first)
  }

  // https://github.com/square/workflow-kotlin/issues/223
  @Test fun `snapshots are lazy`() {
    lateinit var sink: Sink<String>
    var snapped = false

    val workflow = Workflow.stateful<Unit, String, Nothing, String>(
      initialState = { _, _ -> "unchanging state" },
      snapshot = {
        Snapshot.of {
          snapped = true
          ByteString.of(1)
        }
      },
      render = { _, renderState ->
        sink = actionSink.contraMap { action { state = it } }
        renderState
      }
    )
    val props = MutableStateFlow(Unit)
    val renderings = renderWorkflowIn(workflow, testScope, props) {}

    val emitted = mutableListOf<RenderingAndSnapshot<String>>()
    val scope = CoroutineScope(Unconfined)
    scope.launch {
      renderings.collect {
        emitted += it
      }
    }
    sink.send("unchanging state")
    sink.send("unchanging state")
    scope.cancel()

    assertFalse(snapped)
    assertNotSame(emitted[0].snapshot.workflowSnapshot, emitted[1].snapshot.workflowSnapshot)
    assertNotSame(emitted[1].snapshot.workflowSnapshot, emitted[2].snapshot.workflowSnapshot)
  }

  @Test fun `onOutput called when output emitted`() {
    val trigger = Channel<String>()
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(
        trigger.consumeAsFlow()
          .asWorker()
      ) { action { setOutput(it) } }
    }
    val receivedOutputs = mutableListOf<String>()
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {
      receivedOutputs += it
    }
    assertTrue(receivedOutputs.isEmpty())

    trigger.trySend("foo").isSuccess
    assertEquals(listOf("foo"), receivedOutputs)

    trigger.trySend("bar").isSuccess
    assertEquals(listOf("foo", "bar"), receivedOutputs)
  }

  @Test fun `onOutput is not called when no output emitted`() {
    val workflow = Workflow.stateless<Int, String, Int> { props -> props }
    var onOutputCalls = 0
    val props = MutableStateFlow(0)
    val renderings = renderWorkflowIn(workflow, testScope, props) { onOutputCalls++ }
    assertEquals(0, renderings.value.rendering)
    assertEquals(0, onOutputCalls)

    props.value = 1
    assertEquals(1, renderings.value.rendering)
    assertEquals(0, onOutputCalls)

    props.value = 2
    assertEquals(2, renderings.value.rendering)
    assertEquals(0, onOutputCalls)
  }

  /**
   * Since the initial render occurs before launching the coroutine, an exception thrown from it
   * doesn't implicitly cancel the scope. If it did, the reception would be reported twice: once to
   * the caller, and once to the scope.
   */
  @Test fun `exception from initial render doesn't fail parent scope`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      throw ExpectedException()
    }
    assertFailsWith<ExpectedException> {
      renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    }
    assertTrue(testScope.isActive)
  }

  @Test
  fun `side effects from initial rendering in root workflow are never started when initial render of root workflow fails`() { // ktlint-disable max-line-length
    var sideEffectWasRan = false
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        sideEffectWasRan = true
      }
      throw ExpectedException()
    }

    assertFailsWith<ExpectedException> {
      renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    }
    assertFalse(sideEffectWasRan)
  }

  @Test
  fun `side effects from initial rendering in non-root workflow are cancelled when initial render of root workflow fails`() { // ktlint-disable max-line-length
    var sideEffectWasRan = false
    var cancellationException: Throwable? = null
    val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        sideEffectWasRan = true
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { cause -> cancellationException = cause }
        }
      }
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(childWorkflow)
      throw ExpectedException()
    }

    assertFailsWith<ExpectedException> {
      renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    }
    assertTrue(sideEffectWasRan)
    assertNotNull(cancellationException)
    val realCause = generateSequence(cancellationException) { it.cause }
      .firstOrNull { it !is CancellationException }
    assertTrue(realCause is ExpectedException)
  }

  @Test
  fun `side effects from initial rendering in non-root workflow are never started when initial render of non-root workflow fails`() { // ktlint-disable max-line-length
    var sideEffectWasRan = false
    val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect("test") {
        sideEffectWasRan = true
      }
      throw ExpectedException()
    }
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderChild(childWorkflow)
    }

    assertFailsWith<ExpectedException> {
      renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    }
    assertFalse(sideEffectWasRan)
  }

  @Test fun `exception from non-initial render fails parent scope`() {
    val trigger = CompletableDeferred<Unit>()
    // Throws an exception when trigger is completed.
    val workflow = Workflow.stateful<Unit, Boolean, Nothing, Unit>(
      initialState = { false },
      render = { _, throwNow ->
        runningWorker(Worker.from { trigger.await() }) { action { state = true } }
        if (throwNow) {
          throw ExpectedException()
        }
      }
    )
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}

    assertTrue(testScope.isActive)

    trigger.complete(Unit)
    assertFalse(testScope.isActive)
  }

  @Test fun `exception from action fails parent scope`() {
    val trigger = CompletableDeferred<Unit>()
    // Throws an exception when trigger is completed.
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningWorker(Worker.from { trigger.await() }) {
        action {
          throw ExpectedException()
        }
      }
    }
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}

    assertTrue(testScope.isActive)

    trigger.complete(Unit)
    assertFalse(testScope.isActive)
  }

  @Test fun `cancelling scope cancels runtime`() {
    var cancellationException: Throwable? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect(key = "test1") {
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { cause -> cancellationException = cause }
        }
      }
    }
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    assertNull(cancellationException)
    assertTrue(testScope.isActive)

    testScope.cancel()
    assertTrue(cancellationException is CancellationException)
    assertNull(cancellationException!!.cause)
  }

  @Test fun `cancelling scope in action cancels runtime and does not render again`() {
    val trigger = CompletableDeferred<Unit>()
    var renderCount = 0
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      renderCount++
      runningWorker(Worker.from { trigger.await() }) {
        action {
          testScope.cancel()
        }
      }
    }
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    assertTrue(testScope.isActive)
    assertTrue(renderCount == 1)

    trigger.complete(Unit)
    testScope.advanceUntilIdle()
    assertFalse(testScope.isActive)
    assertEquals(1, renderCount, "Should not render after CoroutineScope is canceled.")
  }

  @Test fun `failing scope cancels runtime`() {
    var cancellationException: Throwable? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect(key = "failing") {
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { cause -> cancellationException = cause }
        }
      }
    }
    renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}
    assertNull(cancellationException)
    assertTrue(testScope.isActive)

    testScope.cancel(CancellationException("fail!", ExpectedException()))
    assertTrue(cancellationException is CancellationException)
    assertTrue(cancellationException!!.cause is ExpectedException)
  }

  @Test fun `error from renderings collector doesn't fail parent scope`() {
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
    val renderings = renderWorkflowIn(workflow, testScope, MutableStateFlow(Unit)) {}

    // Collect in separate scope so we actually test that the parent scope is failed when it's
    // different from the collecting scope.
    val collectScope = TestScope(UnconfinedTestDispatcher())
    collectScope.launch {
      renderings.collect { throw ExpectedException() }
    }
    assertTrue(testScope.isActive)
    assertFalse(collectScope.isActive)
  }

  @Test fun `error from renderings collector cancels runtime`() {
    var cancellationException: Throwable? = null
    val workflow = Workflow.stateless<Unit, Nothing, Unit> {
      runningSideEffect(key = "test") {
        suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { cause ->
            cancellationException = cause
          }
        }
      }
    }
    val renderings = renderWorkflowIn(workflow, pausedTestScope, MutableStateFlow(Unit)) {}

    pausedTestScope.launch {
      renderings.collect { throw ExpectedException() }
    }
    assertNull(cancellationException)

    pausedTestScope.advanceUntilIdle()
    assertTrue(cancellationException is CancellationException)
    assertTrue(cancellationException!!.cause is ExpectedException)
  }

  @Test fun `exception from onOutput fails parent scope`() {
    val trigger = CompletableDeferred<Unit>()
    // Emits a Unit when trigger is completed.
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(Worker.from { trigger.await() }) { action { setOutput(Unit) } }
    }
    renderWorkflowIn(workflow, pausedTestScope, MutableStateFlow(Unit)) {
      throw ExpectedException()
    }
    assertTrue(pausedTestScope.isActive)

    trigger.complete(Unit)
    assertTrue(pausedTestScope.isActive)

    pausedTestScope.advanceUntilIdle()
    assertFalse(pausedTestScope.isActive)
  }

  @Test fun `output is emitted before next render pass`() {
    val outputTrigger = CompletableDeferred<String>()
    // A workflow whose state and rendering is the last output that it emitted.
    val workflow = Workflow.stateful<Unit, String, String, String>(
      initialState = { "{no output}" },
      render = { _, renderState ->
        runningWorker(Worker.from { outputTrigger.await() }) { output ->
          action {
            setOutput(output)
            state = output
          }
        }
        return@stateful renderState
      }
    )
    val events = mutableListOf<String>()

    renderWorkflowIn(
      workflow, pausedTestScope, MutableStateFlow(Unit), onOutput = { events += "output($it)" }
    )
      .onEach { events += "rendering(${it.rendering})" }
      .launchIn(pausedTestScope)
    pausedTestScope.runCurrent()
    assertEquals(listOf("rendering({no output})"), events)

    outputTrigger.complete("output")
    pausedTestScope.runCurrent()
    assertEquals(
      listOf(
        "rendering({no output})",
        "output(output)",
        "rendering(output)",
      ),
      events
    )
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun `exceptions from Snapshots don't fail runtime`() {
    val workflow = Workflow.stateful<Int, Unit, Nothing, Unit>(
      snapshot = {
        Snapshot.of {
          throw ExpectedException()
        }
      },
      initialState = { _, _ -> },
      render = { _, _ -> }
    )
    val props = MutableStateFlow(0)
    val uncaughtExceptions = mutableListOf<Throwable>()
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      uncaughtExceptions += throwable
    }
    val snapshot = renderWorkflowIn(workflow, testScope + exceptionHandler, props) {}
      .value
      .snapshot

    assertFailsWith<ExpectedException> { snapshot.toByteString() }
    assertTrue(uncaughtExceptions.isEmpty())

    props.value += 1
    assertFailsWith<ExpectedException> { snapshot.toByteString() }
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun `exceptions from renderings' equals methods don't fail runtime`() {
    @Suppress("EqualsOrHashCode", "unused")
    class FailRendering(val value: Int) {
      override fun equals(other: Any?): Boolean {
        throw ExpectedException()
      }
    }

    val workflow = Workflow.stateless<Int, Nothing, FailRendering> { props ->
      FailRendering(props)
    }
    val props = MutableStateFlow(0)
    val uncaughtExceptions = mutableListOf<Throwable>()
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      uncaughtExceptions += throwable
    }
    val ras = renderWorkflowIn(workflow, testScope + exceptionHandler, props) {}
    val renderings = ras.map { it.rendering }
      .produceIn(testScope)

    @Suppress("UnusedEquals")
    assertFailsWith<ExpectedException> { renderings.tryReceive().getOrNull()!!.equals(Unit) }
    assertTrue(uncaughtExceptions.isEmpty())

    // Trigger another render pass.
    props.value += 1
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun `exceptions from renderings' hashCode methods don't fail runtime`() {
    @Suppress("EqualsOrHashCode")
    data class FailRendering(val value: Int) {
      override fun hashCode(): Int {
        throw ExpectedException()
      }
    }

    val workflow = Workflow.stateless<Int, Nothing, FailRendering> { props ->
      FailRendering(props)
    }
    val props = MutableStateFlow(0)
    val uncaughtExceptions = mutableListOf<Throwable>()
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      uncaughtExceptions += throwable
    }
    val ras = renderWorkflowIn(workflow, testScope + exceptionHandler, props) {}
    val renderings = ras.map { it.rendering }
      .produceIn(testScope)

    @Suppress("UnusedEquals")
    assertFailsWith<ExpectedException> { renderings.tryReceive().getOrNull().hashCode() }
    assertTrue(uncaughtExceptions.isEmpty())

    props.value += 1
    @Suppress("UnusedEquals")
    assertFailsWith<ExpectedException> { renderings.tryReceive().getOrNull().hashCode() }
  }

  private class ExpectedException : RuntimeException()
}
