package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.internal.ParameterizedTestRunner
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, WorkflowExperimentalRuntime::class)
class RenderWorkflowInTest {

  /**
   * A [TestScope] that will not run until explicitly told to.
   */
  private lateinit var pausedTestScope: TestScope

  /**
   * A [TestScope] that will automatically dispatch enqueued routines.
   */
  private lateinit var testScope: TestScope

  private val runtimeOptions: Sequence<RuntimeConfig> = arrayOf(
    RuntimeConfigOptions.RENDER_PER_ACTION,
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(CONFLATE_STALE_RENDERINGS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
  ).asSequence()

  private val runtimeTestRunner = ParameterizedTestRunner<RuntimeConfig>()

  private fun setup() {
    pausedTestScope = TestScope()
    testScope = TestScope(UnconfinedTestDispatcher())
  }

  @Test fun initial_rendering_is_calculated_synchronously() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val props = MutableStateFlow("foo")
      val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
      // Don't allow the workflow runtime to actually start.

      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = pausedTestScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      assertEquals("props: foo", renderings.value.rendering)
    }
  }

  @Test fun initial_rendering_is_calculated_when_scope_cancelled_before_start() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val props = MutableStateFlow("foo")
      val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }

      pausedTestScope.cancel()
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = pausedTestScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      assertEquals("props: foo", renderings.value.rendering)
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_root_workflow_are_never_started_when_scope_cancelled_before_start`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      var sideEffectWasRan = false
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect("test") {
          sideEffectWasRan = true
        }
      }

      testScope.cancel()
      renderWorkflowIn(
        workflow,
        testScope,
        MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      testScope.advanceUntilIdle()

      assertFalse(sideEffectWasRan)
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_non_root_workflow_are_never_started_when_scope_cancelled_before_start`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      testScope.advanceUntilIdle()

      assertFalse(sideEffectWasRan)
    }
  }

  @Test fun new_renderings_are_emitted_on_update() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val props = MutableStateFlow("foo")
      val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      assertEquals("props: foo", renderings.value.rendering)

      props.value = "bar"
      testScope.advanceUntilIdle()
      testScope.runCurrent()

      assertEquals("props: bar", renderings.value.rendering)
    }
  }

  private val runtimeMatrix: Sequence<Pair<RuntimeConfig, RuntimeConfig>> = arrayOf(
    Pair(RuntimeConfigOptions.RENDER_PER_ACTION, RuntimeConfigOptions.RENDER_PER_ACTION),
    Pair(RuntimeConfigOptions.RENDER_PER_ACTION, setOf(RENDER_ONLY_WHEN_STATE_CHANGES)),
    Pair(RuntimeConfigOptions.RENDER_PER_ACTION, setOf(CONFLATE_STALE_RENDERINGS)),
    Pair(
      RuntimeConfigOptions.RENDER_PER_ACTION,
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
    ),
    Pair(setOf(RENDER_ONLY_WHEN_STATE_CHANGES), RuntimeConfigOptions.RENDER_PER_ACTION),
    Pair(setOf(RENDER_ONLY_WHEN_STATE_CHANGES), setOf(RENDER_ONLY_WHEN_STATE_CHANGES)),
    Pair(setOf(RENDER_ONLY_WHEN_STATE_CHANGES), setOf(CONFLATE_STALE_RENDERINGS)),
    Pair(
      setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
    ),
    Pair(setOf(CONFLATE_STALE_RENDERINGS), RuntimeConfigOptions.RENDER_PER_ACTION),
    Pair(setOf(CONFLATE_STALE_RENDERINGS), setOf(RENDER_ONLY_WHEN_STATE_CHANGES)),
    Pair(setOf(CONFLATE_STALE_RENDERINGS), setOf(CONFLATE_STALE_RENDERINGS)),
    Pair(
      setOf(CONFLATE_STALE_RENDERINGS),
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
    ),
    Pair(
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
      RuntimeConfigOptions.RENDER_PER_ACTION
    ),
    Pair(
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
      setOf(RENDER_ONLY_WHEN_STATE_CHANGES)
    ),
    Pair(
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
      setOf(CONFLATE_STALE_RENDERINGS)
    ),
    Pair(
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
      setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
    ),
  ).asSequence()
  private val runtimeMatrixTestRunner =
    ParameterizedTestRunner<Pair<RuntimeConfig, RuntimeConfig>>()

  @Test fun saves_to_and_restores_from_snapshot() {
    runtimeMatrixTestRunner.runParametrizedTest(
      paramSource = runtimeMatrix,
      before = ::setup,
    ) { (runtimeConfig1, runtimeConfig2) ->
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
            { newState -> actionSink.send(action("") { state = newState }) }
          )
        }
      )
      val props = MutableStateFlow(Unit)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = props,
        runtimeConfig = runtimeConfig1,
        workflowTracer = null,
      ) {}

      // Interact with the workflow to change the state.
      renderings.value.rendering.let { (state, updateState) ->
        runtimeMatrixTestRunner.assertEquals("initial state", state)
        updateState("updated state")
      }

      testScope.advanceUntilIdle()
      testScope.runCurrent()

      val snapshot = renderings.value.let { (rendering, snapshot) ->
        val (state, updateState) = rendering
        runtimeMatrixTestRunner.assertEquals("updated state", state)
        updateState("ignored rendering")
        return@let snapshot
      }

      // Create a new scope to launch a second runtime to restore.
      val restoreScope = TestScope()
      val restoredRenderings =
        renderWorkflowIn(
          workflow = workflow,
          scope = restoreScope,
          props = props,
          initialSnapshot = snapshot,
          workflowTracer = null,
          runtimeConfig = runtimeConfig2
        ) {}
      runtimeMatrixTestRunner.assertEquals(
        "updated state",
        restoredRenderings.value.rendering.first
      )
    }
  }

  // https://github.com/square/workflow-kotlin/issues/223
  @Test fun snapshots_are_lazy() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
          sink = actionSink.contraMap { action("") { state = it } }
          renderState
        }
      )
      val props = MutableStateFlow(Unit)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      val emitted = mutableListOf<RenderingAndSnapshot<String>>()
      val scope = CoroutineScope(Unconfined)
      scope.launch {
        renderings.collect { emitted += it }
      }

      if (runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES)) {
        // we have to change state then or it won't render.
        sink.send("changing state")
      } else {
        sink.send("unchanging state")
      }
      testScope.advanceUntilIdle()
      testScope.runCurrent()

      if (runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES)) {
        // we have to change state then or it won't render.
        sink.send("changing state, again")
      } else {
        sink.send("unchanging state")
      }
      testScope.advanceUntilIdle()
      testScope.runCurrent()

      scope.cancel()

      assertFalse(snapped)
      assertNotSame(
        emitted[0].snapshot.workflowSnapshot,
        emitted[1].snapshot.workflowSnapshot
      )
      assertNotSame(
        emitted[1].snapshot.workflowSnapshot,
        emitted[2].snapshot.workflowSnapshot
      )
    }
  }

  @Test fun onOutput_called_when_output_emitted() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val trigger = Channel<String>()
      val workflow = Workflow.stateless<Unit, String, Unit> {
        runningWorker(
          trigger.consumeAsFlow()
            .asWorker()
        ) { action("") { setOutput(it) } }
      }
      val receivedOutputs = mutableListOf<String>()
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {
        receivedOutputs += it
      }
      assertTrue(receivedOutputs.isEmpty())

      trigger.trySend("foo").isSuccess
      assertEquals(listOf("foo"), receivedOutputs)

      trigger.trySend("bar").isSuccess
      assertEquals(listOf("foo", "bar"), receivedOutputs)
    }
  }

  @Test fun onOutput_called_after_rendering_emitted() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val trigger = Channel<String>()
      val workflow = Workflow.stateful<String, String, String>(
        initialState = "initial",
        render = { renderState ->
          runningWorker(
            trigger.consumeAsFlow()
              .asWorker()
          ) {
            action("") {
              state = it
              setOutput(it)
            }
          }
          renderState
        }
      )

      val emittedRenderings = mutableListOf<String>()
      val receivedOutputs = mutableListOf<String>()
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) { it: String ->
        receivedOutputs += it
        assertTrue(emittedRenderings.contains(it))
      }
      assertTrue(receivedOutputs.isEmpty())

      val scope = CoroutineScope(Unconfined)
      scope.launch {
        renderings.collect { rendering: RenderingAndSnapshot<String> ->
          emittedRenderings += rendering.rendering
        }
      }

      trigger.trySend("foo").isSuccess

      trigger.trySend("bar").isSuccess

      scope.cancel()
    }
  }

  @Test fun onOutput_is_not_called_when_no_output_emitted() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val workflow = Workflow.stateless<Int, String, Int> { props -> props }
      var onOutputCalls = 0
      val props = MutableStateFlow(0)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) { onOutputCalls++ }
      assertEquals(0, renderings.value.rendering)
      assertEquals(0, onOutputCalls)

      props.value = 1
      testScope.advanceUntilIdle()
      testScope.runCurrent()
      assertEquals(1, renderings.value.rendering)
      assertEquals(0, onOutputCalls)

      props.value = 2
      testScope.advanceUntilIdle()
      testScope.runCurrent()
      assertEquals(2, renderings.value.rendering)
      assertEquals(0, onOutputCalls)
    }
  }

  /**
   * Since the initial render occurs before launching the coroutine, an exception thrown from it
   * doesn't implicitly cancel the scope. If it did, the reception would be reported twice: once to
   * the caller, and once to the scope.
   */
  @Test fun exception_from_initial_render_does_not_fail_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        throw ExpectedException()
      }
      assertFailsWith<ExpectedException> {
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
        ) {}
      }
      assertTrue(testScope.isActive)
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_root_workflow_are_never_started_when_initial_render_of_root_workflow_fails`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      var sideEffectWasRan = false
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect("test") {
          sideEffectWasRan = true
        }
        throw ExpectedException()
      }

      assertFailsWith<ExpectedException> {
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
        ) {}
      }
      assertFalse(sideEffectWasRan)
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_non_root_workflow_are_cancelled_when_initial_render_of_root_workflow_fails`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
        ) {}
      }
      assertTrue(sideEffectWasRan)
      assertNotNull(cancellationException)
      val realCause = generateSequence(cancellationException) { it.cause }
        .firstOrNull { it !is CancellationException }
      assertTrue(realCause is ExpectedException)
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_non_root_workflow_are_never_started_when_initial_render_of_non_root_workflow_fails`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
        ) {}
      }
      assertFalse(sideEffectWasRan)
    }
  }

  @Test fun exception_from_non_initial_render_fails_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val trigger = CompletableDeferred<Unit>()
      // Throws an exception when trigger is completed.
      val workflow = Workflow.stateful<Unit, Boolean, Nothing, Unit>(
        initialState = { false },
        render = { _, throwNow ->
          runningWorker(Worker.from { trigger.await() }) { action("") { state = true } }
          if (throwNow) {
            throw ExpectedException()
          }
        }
      )
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      assertTrue(testScope.isActive)

      trigger.complete(Unit)
      testScope.advanceUntilIdle()
      testScope.runCurrent()
      assertFalse(testScope.isActive)
    }
  }

  @Test fun exception_from_action_fails_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val trigger = CompletableDeferred<Unit>()
      // Throws an exception when trigger is completed.
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningWorker(Worker.from { trigger.await() }) {
          action("") {
            throw ExpectedException()
          }
        }
      }
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      assertTrue(testScope.isActive)

      trigger.complete(Unit)
      testScope.advanceUntilIdle()
      testScope.runCurrent()
      assertFalse(testScope.isActive)
    }
  }

  @Test fun cancelling_scope_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      var cancellationException: Throwable? = null
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect(key = "test1") {
          suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cause -> cancellationException = cause }
          }
        }
      }
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      assertNull(cancellationException)
      assertTrue(testScope.isActive)

      testScope.cancel()
      assertTrue(cancellationException is CancellationException)
      assertNull(cancellationException!!.cause)
    }
  }

  @Test fun cancelling_scope_in_action_cancels_runtime_and_does_not_render_again() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val trigger = CompletableDeferred<Unit>()
      var renderCount = 0
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        renderCount++
        runningWorker(Worker.from { trigger.await() }) {
          action("") {
            testScope.cancel()
          }
        }
      }
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      assertTrue(testScope.isActive)
      assertTrue(renderCount == 1)

      trigger.complete(Unit)
      testScope.advanceUntilIdle()
      assertFalse(testScope.isActive)
      assertEquals(
        1,
        renderCount,
        "Should not render after CoroutineScope is canceled."
      )
    }
  }

  @Test fun failing_scope_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      var cancellationException: Throwable? = null
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect(key = "failing") {
          suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cause -> cancellationException = cause }
          }
        }
      }
      renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      assertNull(cancellationException)
      assertTrue(testScope.isActive)

      testScope.cancel(CancellationException("fail!", ExpectedException()))
      assertTrue(cancellationException is CancellationException)
      assertTrue(cancellationException!!.cause is ExpectedException)
    }
  }

  @Test fun error_from_renderings_collector_does_not_fail_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      // Collect in separate scope so we actually test that the parent scope is failed when it's
      // different from the collecting scope.
      val collectScope = TestScope(UnconfinedTestDispatcher())
      collectScope.launch {
        renderings.collect { throw ExpectedException() }
      }
      assertTrue(testScope.isActive)
      assertFalse(collectScope.isActive)
    }
  }

  @Test fun error_from_renderings_collector_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = pausedTestScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      pausedTestScope.launch {
        renderings.collect { throw ExpectedException() }
      }
      assertNull(cancellationException)

      pausedTestScope.advanceUntilIdle()
      assertTrue(cancellationException is CancellationException)
      assertTrue(cancellationException!!.cause is ExpectedException)
    }
  }

  @Test fun exception_from_onOutput_fails_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val trigger = CompletableDeferred<Unit>()
      // Emits a Unit when trigger is completed.
      val workflow = Workflow.stateless<Unit, Unit, Unit> {
        runningWorker(Worker.from { trigger.await() }) { action("") { setOutput(Unit) } }
      }
      renderWorkflowIn(
        workflow = workflow,
        scope = pausedTestScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {
        throw ExpectedException()
      }
      assertTrue(pausedTestScope.isActive)

      trigger.complete(Unit)
      assertTrue(pausedTestScope.isActive)

      pausedTestScope.advanceUntilIdle()
      pausedTestScope.runCurrent()
      assertFalse(pausedTestScope.isActive)
    }
  }

  @Test fun output_is_emitted_before_next_render_pass() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      val outputTrigger = CompletableDeferred<String>()
      // A workflow whose state and rendering is the last output that it emitted.
      val workflow = Workflow.stateful<Unit, String, String, String>(
        initialState = { "{no output}" },
        render = { _, renderState ->
          runningWorker(Worker.from { outputTrigger.await() }) { output ->
            action("") {
              setOutput(output)
              state = output
            }
          }
          return@stateful renderState
        }
      )
      val events = mutableListOf<String>()

      renderWorkflowIn(
        workflow = workflow,
        scope = pausedTestScope,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
        onOutput = { events += "output($it)" }
      )
        .onEach { events += "rendering(${it.rendering})" }
        .launchIn(pausedTestScope)
      pausedTestScope.advanceUntilIdle()
      pausedTestScope.runCurrent()
      assertEquals(listOf("rendering({no output})"), events)

      outputTrigger.complete("output")
      pausedTestScope.advanceUntilIdle()
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
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun exceptions_from_Snapshots_do_not_fail_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
      val snapshot = renderWorkflowIn(
        workflow = workflow,
        scope = testScope + exceptionHandler,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
        .value
        .snapshot

      assertFailsWith<ExpectedException> { snapshot.toByteString() }
      assertTrue(uncaughtExceptions.isEmpty())

      props.value += 1
      assertFailsWith<ExpectedException> { snapshot.toByteString() }
    }
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun exceptions_from_renderings_equals_methods_do_not_fail_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
      val ras = renderWorkflowIn(
        workflow = workflow,
        scope = testScope + exceptionHandler,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      val renderings = ras.map { it.rendering }
        .produceIn(testScope)

      testScope.advanceUntilIdle()
      testScope.runCurrent()

      @Suppress("UnusedEquals")
      assertFailsWith<ExpectedException> {
        renderings.tryReceive()
          .getOrNull()!!
          .equals(Unit)
      }
      assertTrue(uncaughtExceptions.isEmpty())

      // Trigger another render pass.
      props.value += 1
      testScope.advanceUntilIdle()
      testScope.runCurrent()
    }
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun exceptions_from_renderings_hashCode_methods_do_not_fail_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
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
      val ras = renderWorkflowIn(
        workflow = workflow,
        scope = testScope + exceptionHandler,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}
      val renderings = ras.map { it.rendering }
        .produceIn(testScope)

      assertFailsWith<ExpectedException> {
        renderings.tryReceive()
          .getOrNull()
          .hashCode()
      }
      assertTrue(uncaughtExceptions.isEmpty())

      props.value += 1
      testScope.advanceUntilIdle()
      testScope.runCurrent()

      assertFailsWith<ExpectedException> {
        renderings.tryReceive()
          .getOrNull()
          .hashCode()
      }
    }
  }

  @Test fun for_render_on_state_change_only_we_do_not_render_if_state_not_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = arrayOf(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS)
      ).asSequence(),
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))
      lateinit var sink: Sink<String>

      val workflow = Workflow.stateful<Unit, String, Nothing, String>(
        initialState = { "unchanging state" },
        render = { _, renderState ->
          sink = actionSink.contraMap { action("") { state = it } }
          renderState
        }
      )
      val props = MutableStateFlow(Unit)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      val emitted = mutableListOf<RenderingAndSnapshot<String>>()
      val scope = CoroutineScope(Unconfined)
      scope.launch {
        renderings.collect { emitted += it }
      }

      sink.send("unchanging state")
      testScope.advanceUntilIdle()
      testScope.runCurrent()
      scope.cancel()

      assertEquals(1, emitted.size)
    }
  }

  @Test fun for_render_on_state_change_only_we_render_if_state_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = arrayOf(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS)
      ).asSequence(),
      before = ::setup,
    ) { runtimeConfig: RuntimeConfig ->
      check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))
      lateinit var sink: Sink<String>

      val workflow = Workflow.stateful<Unit, String, Nothing, String>(
        initialState = { "unchanging state" },
        render = { _, renderState ->
          sink = actionSink.contraMap { action("") { state = it } }
          renderState
        }
      )
      val props = MutableStateFlow(Unit)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = testScope,
        props = props,
        runtimeConfig = runtimeConfig,
        workflowTracer = null,
      ) {}

      val emitted = mutableListOf<RenderingAndSnapshot<String>>()
      val scope = CoroutineScope(Unconfined)
      scope.launch {
        renderings.collect { emitted += it }
      }

      sink.send("changing state")
      testScope.advanceUntilIdle()
      testScope.runCurrent()
      scope.cancel()

      assertEquals(2, emitted.size)
    }
  }

  private class ExpectedException : RuntimeException()
}
