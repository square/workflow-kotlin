package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.DRAIN_EXCLUSIVE_ACTIONS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.WorkflowInterceptor.RenderPassSkipped
import com.squareup.workflow1.WorkflowInterceptor.RenderPassesComplete
import com.squareup.workflow1.WorkflowInterceptor.RuntimeLoopOutcome
import com.squareup.workflow1.internal.ParameterizedTestRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
class RenderWorkflowInTest {

  private val traces: StringBuilder = StringBuilder()
  private val testTracer: WorkflowTracer = object : WorkflowTracer {
    var prefix: String = ""
    override fun beginSection(label: String) {
      traces.appendLine("${prefix}Starting$label")
      prefix += "  "
    }

    override fun endSection() {
      prefix = prefix.substring(0, prefix.length - 2)
      traces.appendLine("${prefix}Ending")
    }
  }

  private val runtimes = setOf<RuntimeConfig>(
    RuntimeConfigOptions.RENDER_PER_ACTION,
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(CONFLATE_STALE_RENDERINGS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
    setOf(DRAIN_EXCLUSIVE_ACTIONS),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, DRAIN_EXCLUSIVE_ACTIONS),
    setOf(CONFLATE_STALE_RENDERINGS, DRAIN_EXCLUSIVE_ACTIONS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, DRAIN_EXCLUSIVE_ACTIONS),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING, DRAIN_EXCLUSIVE_ACTIONS),
    setOf(
      CONFLATE_STALE_RENDERINGS,
      RENDER_ONLY_WHEN_STATE_CHANGES,
      PARTIAL_TREE_RENDERING,
      DRAIN_EXCLUSIVE_ACTIONS
    ),
  )

  private val tracerOptions = setOf<WorkflowTracer?>(
    null,
    testTracer
  )

  private val myStandardTestDispatcher = StandardTestDispatcher()
  private val dispatcherOptions = setOf<TestDispatcher>(
    UnconfinedTestDispatcher(),
    myStandardTestDispatcher
  )

  private val runtimeOptions: Sequence<Triple<RuntimeConfig, WorkflowTracer?, TestDispatcher>> =
    cartesianProduct(
      runtimes.asSequence(),
      tracerOptions.asSequence(),
      dispatcherOptions.asSequence()
    )

  private val runtimeTestRunner =
    ParameterizedTestRunner<Triple<RuntimeConfig, WorkflowTracer?, TestDispatcher>>()

  private fun setup() {
    traces.clear()
  }

  @Test fun initial_rendering_is_calculated_synchronously() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val props = MutableStateFlow("foo")
        val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
        // Don't allow the workflow runtime to actually start if this is a [StandardTestDispatcher].

        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        assertEquals("props: foo", renderings.value.rendering)
      }
    }
  }

  @Test fun initial_rendering_is_reported_through_interceptor() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val props = MutableStateFlow("foo")
        val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }

        val hasReportedRendering = Mutex(locked = true)
        val testInterceptor = object : WorkflowInterceptor {
          override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
            if (outcome is RenderPassesComplete<*>) {
              assertEquals("props: foo", outcome.renderingAndSnapshot.rendering)
              hasReportedRendering.unlock()
            }
          }
        }
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          interceptors = listOf(testInterceptor),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        hasReportedRendering.lock()
      }
    }
  }

  @Test fun modified_rendering_is_returned() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val props = MutableStateFlow("foo")
        val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }

        val interceptedRenderings = mutableListOf<Any?>()
        val testInterceptor = object : WorkflowInterceptor {
          override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
            if (outcome is RenderPassesComplete<*>) {
              interceptedRenderings.add(outcome.renderingAndSnapshot.rendering)
            }
          }
        }

        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          interceptors = listOf(testInterceptor),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        assertEquals(1, interceptedRenderings.size, "Should have intercepted 1 rendering.")
        assertEquals(
          "props: foo",
          interceptedRenderings[0],
          "Should intercept 'props: foo' as a rendering."
        )
      }
    }
  }

  @Test fun initial_rendering_is_calculated_when_scope_cancelled_before_start() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val props = MutableStateFlow("foo")
        val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }

        val testScope = TestScope(dispatcher)
        testScope.cancel()
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        assertEquals("props: foo", renderings.value.rendering)
      }
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_root_workflow_are_never_started_when_scope_cancelled_before_start`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        var sideEffectWasRan = false
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {
          runningSideEffect("test") {
            sideEffectWasRan = true
          }
        }

        val testScope = TestScope(dispatcher)
        testScope.cancel()
        renderWorkflowIn(
          workflow,
          testScope,
          MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        assertFalse(sideEffectWasRan)
      }
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_non_root_workflow_are_never_started_when_scope_cancelled_before_start`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        var sideEffectWasRan = false
        val childWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
          runningSideEffect("test") {
            sideEffectWasRan = true
          }
        }
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {
          renderChild(childWorkflow)
        }

        val testScope = TestScope(dispatcher)
        testScope.cancel()
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        assertFalse(sideEffectWasRan)
      }
    }
  }

  @Test fun new_renderings_are_emitted_on_update() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val props = MutableStateFlow("foo")
        val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        assertEquals("props: foo", renderings.value.rendering)

        props.value = "bar"
        advanceIfStandard(dispatcher)

        assertEquals("props: bar", renderings.value.rendering)
      }
    }
  }

  @Test fun new_renderings_are_emitted_to_interceptor() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val props = MutableStateFlow("foo")
        val workflow = Workflow.stateless<String, Nothing, String> { "props: $it" }

        val interceptedRenderings = mutableListOf<Any?>()
        val testInterceptor = object : WorkflowInterceptor {
          override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
            if (outcome is RenderPassesComplete<*>) {
              interceptedRenderings.add(outcome.renderingAndSnapshot.rendering)
            }
          }
        }

        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          interceptors = listOf(testInterceptor),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        assertEquals(1, interceptedRenderings.size, "Should have intercepted 1 rendering.")
        assertEquals(
          "props: foo",
          interceptedRenderings[0],
          "Should intercept 'props: foo' as a rendering."
        )

        props.value = "bar"
        advanceIfStandard(dispatcher)

        assertEquals(2, interceptedRenderings.size, "Should have intercepted 2 rendering.")
        assertEquals(
          "props: bar",
          interceptedRenderings[1],
          "Should intercept 'props: bar' as a rendering."
        )
      }
    }
  }

  private val runtimeMatrix: Sequence<Triple<RuntimeConfig, RuntimeConfig, TestDispatcher>> =
    cartesianProduct(
      runtimes.asSequence(),
      runtimes.asSequence(),
      dispatcherOptions.asSequence(),
    )

  private val runtimeMatrixTestRunner =
    ParameterizedTestRunner<Triple<RuntimeConfig, RuntimeConfig, TestDispatcher>>()

  @Test fun saves_to_and_restores_from_snapshot() {
    runtimeMatrixTestRunner.runParametrizedTest(
      paramSource = runtimeMatrix,
      before = ::setup,
    ) { (runtimeConfig1, runtimeConfig2, dispatcher) ->
      runTest(dispatcher) {
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
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig1,
          workflowTracer = null,
        ) {}
        advanceIfStandard(dispatcher)

        // Interact with the workflow to change the state.
        renderings.value.rendering.let { (state, updateState) ->
          runtimeMatrixTestRunner.assertEquals("initial state", state)
          updateState("updated state")
        }
        advanceIfStandard(dispatcher)

        val snapshot = renderings.value.let { (rendering, snapshot) ->
          val (state, updateState) = rendering
          runtimeMatrixTestRunner.assertEquals("updated state", state)
          updateState("ignored rendering")
          return@let snapshot
        }
        advanceIfStandard(dispatcher)

        // Create a new scope to launch a second runtime to restore.
        val restoreScope = TestScope(dispatcher)
        val restoredRenderings =
          renderWorkflowIn(
            workflow = workflow,
            scope = restoreScope,
            props = props,
            initialSnapshot = snapshot,
            workflowTracer = null,
            runtimeConfig = runtimeConfig2
          ) {}
        advanceIfStandard(dispatcher)
        runtimeMatrixTestRunner.assertEquals(
          "updated state",
          restoredRenderings.value.rendering.first
        )
      }
    }
  }

  // https://github.com/square/workflow-kotlin/issues/223
  @Test fun snapshots_are_lazy() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        val emitted = mutableListOf<RenderingAndSnapshot<String>>()
        val collectionJob = launch {
          renderings.collect { emitted += it }
        }
        advanceIfStandard(dispatcher)

        if (runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES)) {
          // we have to change state then or it won't render.
          sink.send("changing state")
        } else {
          sink.send("unchanging state")
        }
        advanceIfStandard(dispatcher)

        if (runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES)) {
          // we have to change state then or it won't render.
          sink.send("changing state, again")
        } else {
          sink.send("unchanging state")
        }
        advanceIfStandard(dispatcher)

        collectionJob.cancel()

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
  }

  @Test fun onOutput_called_when_output_emitted() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val trigger = Channel<String>()
        val workflow = Workflow.stateless<Unit, String, Unit> {
          runningWorker(
            trigger.receiveAsFlow()
              .asWorker()
          ) { action("") { setOutput(it) } }
        }
        val receivedOutputs = mutableListOf<String>()
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          receivedOutputs += it
        }
        advanceIfStandard(dispatcher)
        assertTrue(receivedOutputs.isEmpty())

        assertTrue(trigger.trySend("foo").isSuccess)
        advanceIfStandard(dispatcher)
        assertEquals(listOf("foo"), receivedOutputs)

        assertTrue(trigger.trySend("bar").isSuccess)
        advanceIfStandard(dispatcher)
        assertEquals(listOf("foo", "bar"), receivedOutputs)
      }
    }
  }

  private fun advanceIfStandard(dispatcher: TestDispatcher) {
    if (dispatcher == myStandardTestDispatcher) {
      dispatcher.scheduler.advanceUntilIdle()
      dispatcher.scheduler.runCurrent()
    }
  }

  /**
   * This is a bit of a tricky test. Everything comes down to how your coroutines are dispatched.
   * This test confirms that we are setting the value on the StateFlow of the updated rendering
   * before onOutput is called.
   *
   * If we were collecting the renderings, that would happen after [onOutput] as it would have
   * to wait to be dispatched after onOutput was complete.
   *
   * See [onOutput_called_after_rendering_emitted_and_collected] for alternate behaviour with
   * a different dispatcher for the runtime.
   */
  @Test fun onOutput_called_after_rendering_emitted() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val trigger = Channel<String>()
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "initial",
          render = { renderState ->
            runningWorker(
              trigger.receiveAsFlow()
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

        val receivedOutputs = mutableListOf<String>()
        lateinit var renderings: StateFlow<RenderingAndSnapshot<String>>
        renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { it: String ->
          receivedOutputs += it
          // The value of the updated rendering has already been set by the time onOutput is
          // called
          assertEquals(it, renderings.value.rendering)
        }
        advanceIfStandard(dispatcher)

        assertTrue(receivedOutputs.isEmpty())

        assertTrue(trigger.trySend("foo").isSuccess)
        advanceIfStandard(dispatcher)
        assertEquals(listOf("foo"), receivedOutputs)
      }
    }
  }

  /**
   * A different form of [onOutput_called_after_rendering_emitted]. Here we launch the workflow
   * runtime on its own [TestScope] with a [StandardTestDispatcher], which will be paused until
   * told to advance.
   *
   * We *collect* emitted renderings on the [UnconfinedTestDispatcher] of the [runTest].
   * The point here is that when the runtime sets the value on the StateFlow - a non-suspending
   * operation - and then it calls [onOutput] - a suspending operation - the [onOutput] handler
   * will not be immediately dispatched (it is waiting for dispatch from the scheduler), but the
   * collector of the renderings [StateFlow] will be dispatched and update the 'emitted' renderings.
   * Then when we let the runtime's scheduler go ahead, it will have already been populated.
   */
  @Test fun onOutput_called_after_rendering_emitted_and_collected() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter { it.third != myStandardTestDispatcher },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val trigger = Channel<String>()
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "initial",
          render = { renderState ->
            runningWorker(
              trigger.receiveAsFlow()
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

        val runtimeTestDispatcher = StandardTestDispatcher()
        val testScope = TestScope(runtimeTestDispatcher)
        val emittedRenderings = mutableListOf<String>()
        val receivedOutputs = mutableListOf<String>()
        val renderings: StateFlow<RenderingAndSnapshot<String>> = renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { it: String ->
          // The list collecting the renderings already contains it by the time onOutput is fired.
          assertTrue(emittedRenderings.contains(it))
          receivedOutputs += it
        }
        assertTrue(receivedOutputs.isEmpty())

        val collectionJob = launch {
          renderings.collect {
            emittedRenderings += it.rendering
          }
        }

        launch {
          trigger.send("foo")
        }

        testScope.advanceUntilIdle()

        assertEquals(listOf("foo"), receivedOutputs)

        collectionJob.cancel()
      }
    }
  }

  @Test fun tracer_includes_expected_sections() = runTest(UnconfinedTestDispatcher()) {
    // Only test default so we only have one 'golden value' to assert against.
    // We are only testing the tracer correctness here, which should be agnostic of runtime.
    // We include 'tracers' in the other test to test against unexpected side effects.
    val runtimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG
    val workflowTracer = testTracer
    setup()
    val trigger = Channel<String>()
    val workflow = Workflow.stateful<String, String, String>(
      initialState = "initial",
      render = { renderState ->
        runningWorker(
          trigger.receiveAsFlow()
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
      scope = backgroundScope,
      props = MutableStateFlow(Unit),
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      onOutput = {}
    )
    assertTrue(receivedOutputs.isEmpty())

    val collectionJob = launch {
      renderings.collect { rendering: RenderingAndSnapshot<String> ->
        emittedRenderings += rendering.rendering
      }
    }

    assertTrue(trigger.trySend("foo").isSuccess)

    assertEquals(EXPECTED_TRACE, traces.toString().trim())

    collectionJob.cancel()
  }

  @Test fun onOutput_is_not_called_when_no_output_emitted() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val workflow = Workflow.stateless<Int, String, Int> { props -> props }
        var onOutputCalls = 0
        val props = MutableStateFlow(0)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { onOutputCalls++ }
        advanceIfStandard(dispatcher)
        assertEquals(0, renderings.value.rendering)
        assertEquals(0, onOutputCalls)

        props.value = 1
        advanceIfStandard(dispatcher)
        assertEquals(1, renderings.value.rendering)
        assertEquals(0, onOutputCalls)

        props.value = 2
        advanceIfStandard(dispatcher)
        assertEquals(2, renderings.value.rendering)
        assertEquals(0, onOutputCalls)
      }
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
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {
          throw ExpectedException()
        }
        assertFailsWith<ExpectedException> {
          renderWorkflowIn(
            workflow = workflow,
            scope = backgroundScope,
            props = MutableStateFlow(Unit),
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
        }
        assertTrue(backgroundScope.isActive)
      }
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_root_workflow_are_never_started_when_initial_render_of_root_workflow_fails`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
            scope = backgroundScope,
            props = MutableStateFlow(Unit),
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
        }
        assertFalse(sideEffectWasRan)
      }
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_non_root_workflow_are_cancelled_when_initial_render_of_root_workflow_fails`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
            scope = backgroundScope,
            props = MutableStateFlow(Unit),
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
        }
        advanceIfStandard(dispatcher)
        if (dispatcher != myStandardTestDispatcher) {
          // Side effect will never actually be started unless the dispatcher is eager.
          assertTrue(sideEffectWasRan)
          assertNotNull(cancellationException)
          val realCause = generateSequence(cancellationException) { it.cause }
            .firstOrNull { it !is CancellationException }
          assertTrue(realCause is ExpectedException)
        }
      }
    }
  }

  @Test
  fun `side_effects_from_initial_rendering_in_non_root_workflow_are_never_started_when_initial_render_of_non_root_workflow_fails`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
            scope = backgroundScope,
            props = MutableStateFlow(Unit),
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
        }
        assertFalse(sideEffectWasRan)
      }
    }
  }

  @Test fun exception_from_non_initial_render_fails_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
        val testScope = TestScope(dispatcher)
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        assertTrue(testScope.isActive)

        trigger.complete(Unit)
        advanceIfStandard(dispatcher)

        assertFalse(testScope.isActive)
      }
    }
  }

  @Test fun exception_from_action_fails_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val trigger = CompletableDeferred<Unit>()
        // Throws an exception when trigger is completed.
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {
          runningWorker(Worker.from { trigger.await() }) {
            action("") {
              throw ExpectedException()
            }
          }
        }
        val testScope = TestScope(dispatcher)
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        assertTrue(testScope.isActive)

        trigger.complete(Unit)
        advanceIfStandard(dispatcher)

        assertFalse(testScope.isActive)
      }
    }
  }

  @Test fun cancelling_scope_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        var cancellationException: Throwable? = null
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {
          runningSideEffect(key = "test1") {
            suspendCancellableCoroutine { continuation ->
              continuation.invokeOnCancellation { cause -> cancellationException = cause }
            }
          }
        }
        val testScope = TestScope(dispatcher)
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        assertNull(cancellationException)
        assertTrue(testScope.isActive)
        advanceIfStandard(dispatcher)

        testScope.cancel()

        advanceIfStandard(dispatcher)

        assertTrue(cancellationException is CancellationException)
        assertNull(cancellationException!!.cause)
      }
    }
  }

  @Test fun cancelling_scope_in_action_cancels_runtime_and_does_not_render_again() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val testScope = TestScope(dispatcher)
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
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        assertTrue(testScope.isActive)
        assertTrue(renderCount == 1)

        trigger.complete(Unit)

        advanceIfStandard(dispatcher)

        assertFalse(testScope.isActive)
        assertEquals(
          1,
          renderCount,
          "Should not render after CoroutineScope is canceled."
        )
      }
    }
  }

  @Test fun failing_scope_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        var cancellationException: Throwable? = null
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {
          runningSideEffect(key = "failing") {
            suspendCancellableCoroutine { continuation ->
              continuation.invokeOnCancellation { cause -> cancellationException = cause }
            }
          }
        }
        val testScope = TestScope(dispatcher)
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)
        assertNull(cancellationException)
        assertTrue(testScope.isActive)

        testScope.cancel(CancellationException("fail!", ExpectedException()))
        advanceIfStandard(dispatcher)
        assertTrue(cancellationException is CancellationException)
        assertTrue(cancellationException!!.cause is ExpectedException)
      }
    }
  }

  @Test fun error_from_renderings_collector_does_not_fail_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
        val testScope = TestScope(dispatcher)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        // Collect in separate scope so we actually test that the parent scope is failed when it's
        // different from the collecting scope.
        val collectScope = TestScope(dispatcher)
        collectScope.launch {
          renderings.collect { throw ExpectedException() }
        }
        advanceIfStandard(dispatcher)
        assertTrue(testScope.isActive)
        assertFalse(collectScope.isActive)
      }
    }
  }

  @Test fun exception_from_onOutput_fails_parent_scope() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        val trigger = CompletableDeferred<Unit>()
        // Emits a Unit when trigger is completed.
        val workflow = Workflow.stateless<Unit, Unit, Unit> {
          runningWorker(Worker.from { trigger.await() }) { action("") { setOutput(Unit) } }
        }
        val testScope = TestScope(dispatcher)
        renderWorkflowIn(
          workflow = workflow,
          scope = testScope,
          props = MutableStateFlow(Unit),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          throw ExpectedException()
        }
        advanceIfStandard(dispatcher)
        assertTrue(testScope.isActive)

        trigger.complete(Unit)
        advanceIfStandard(dispatcher)
        assertFalse(testScope.isActive)
      }
    }
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun exceptions_from_Snapshots_do_not_fail_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
        val mutex = Mutex(locked = true)
        backgroundScope.launch(exceptionHandler) {
          val snapshot = renderWorkflowIn(
            workflow = workflow,
            scope = this,
            props = props,
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
            .value
            .snapshot

          assertFailsWith<ExpectedException> { snapshot.toByteString() }
          assertTrue(uncaughtExceptions.isEmpty())

          props.value += 1
          assertFailsWith<ExpectedException> { snapshot.toByteString() }
          mutex.unlock()
        }
        // wait for snapshotting.
        mutex.lock()
      }
    }
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun exceptions_from_renderings_equals_methods_do_not_fail_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
        val mutex = Mutex(locked = true)
        backgroundScope.launch(exceptionHandler) {
          val ras = renderWorkflowIn(
            workflow = workflow,
            scope = this,
            props = props,
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
          val renderings = ras.map { it.rendering }

          @Suppress("UnusedEquals")
          assertFailsWith<ExpectedException> {
            renderings.collect {
              it.equals(Unit)
            }
          }
          assertTrue(uncaughtExceptions.isEmpty())

          // Trigger another render pass.
          props.value += 1
          advanceIfStandard(dispatcher)
          mutex.unlock()
        }
        mutex.lock()
      }
    }
  }

  // https://github.com/square/workflow-kotlin/issues/224
  @Test fun exceptions_from_renderings_hashCode_methods_do_not_fail_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
        val mutex = Mutex(locked = true)
        backgroundScope.launch(exceptionHandler) {
          val ras = renderWorkflowIn(
            workflow = workflow,
            scope = this,
            props = props,
            runtimeConfig = runtimeConfig,
            workflowTracer = workflowTracer,
          ) {}
          val renderings = ras.map { it.rendering }

          assertFailsWith<ExpectedException> {
            renderings.collect {
              it.hashCode()
            }
          }
          assertTrue(uncaughtExceptions.isEmpty())

          // Trigger another render pass.
          props.value += 1
          advanceIfStandard(dispatcher)
          mutex.unlock()
        }
        mutex.lock()
      }
    }
  }

  @Test fun for_render_on_state_change_only_we_do_not_render_if_state_not_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter {
        it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
      },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        val emitted = mutableListOf<RenderingAndSnapshot<String>>()
        val collectionJob = launch {
          renderings.collect { emitted += it }
        }

        sink.send("unchanging state")
        advanceIfStandard(dispatcher)
        collectionJob.cancel()

        assertEquals(1, emitted.size)
      }
    }
  }

  @Test fun for_render_on_state_change_only_we_report_skipped_in_interceptor() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter {
        it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
      },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))
        lateinit var sink: Sink<String>
        val interceptedRenderings = mutableListOf<Any?>()
        var skippedRenderings = 0
        val testInterceptor = object : WorkflowInterceptor {
          override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
            if (outcome is RenderPassesComplete<*>) {
              interceptedRenderings.add(outcome.renderingAndSnapshot.rendering)
            } else if (outcome is RenderPassSkipped) {
              skippedRenderings++
            }
          }
        }

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
          scope = backgroundScope,
          props = props,
          interceptors = listOf(testInterceptor),
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        val emitted = mutableListOf<RenderingAndSnapshot<String>>()
        val collectionJob = launch {
          renderings.collect { emitted += it }
        }

        sink.send("unchanging state")
        advanceIfStandard(dispatcher)
        collectionJob.cancel()

        assertEquals(1, emitted.size)
        assertEquals(1, interceptedRenderings.size)
        assertEquals(1, skippedRenderings)
      }
    }
  }

  @Test fun for_render_on_state_change_only_we_render_if_state_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter {
        it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
      },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
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
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        val emitted = mutableListOf<RenderingAndSnapshot<String>>()
        val collectionJob = launch {
          renderings.collect { emitted += it }
        }

        advanceIfStandard(dispatcher)
        sink.send("changing state")
        advanceIfStandard(dispatcher)
        assertEquals(2, emitted.size, "Expecting 2 renderings to be emitted.")

        collectionJob.cancel()
      }
    }
  }

  @Test
  fun `for_partial_tree_rendering_we_do_not_render_nodes_if_state_not_changed_even_in_render_pass`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter {
        it.first.contains(PARTIAL_TREE_RENDERING)
      },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(PARTIAL_TREE_RENDERING))

        val trigger = MutableSharedFlow<String>()
        var childRenderCount = 0
        var parentRenderCount = 0

        val childWorkflow = Workflow.stateful<String, String, String>(
          // Starts with "state 1"
          initialState = "state 1",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it // Change state, but no output for parent.
              }
            }
            renderState.also {
              childRenderCount++
            }
          }
        )

        val workflow = Workflow.stateful<String, String, String>(
          initialState = "state 0",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                state = childOutput
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
              }
            }
            renderState.also {
              parentRenderCount++
            }
          }
        )
        val props = MutableStateFlow(Unit)
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}

        advanceIfStandard(dispatcher)
        trigger.emit("state 1") // same value as the child starts with.
        advanceIfStandard(dispatcher)

        assertEquals(2, parentRenderCount)
        assertEquals(1, childRenderCount)
      }
    }
  }

  @Test fun for_partial_tree_rendering_we_render_nodes_if_state_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter {
        it.first.contains(PARTIAL_TREE_RENDERING)
      },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(PARTIAL_TREE_RENDERING))

        val trigger = MutableSharedFlow<String>()
        var childRenderCount = 0
        var parentRenderCount = 0

        val childWorkflow = Workflow.stateful<String, String, String>(
          // Starts with "state 0"
          initialState = "state 0",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it // Change state, but no output for parent.
              }
            }
            renderState.also {
              childRenderCount++
            }
          }
        )

        val workflow = Workflow.stateful<String, String, String>(
          initialState = "state 0",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                state = childOutput
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
              }
            }
            renderState.also {
              parentRenderCount++
            }
          }
        )
        val props = MutableStateFlow(Unit)
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {}
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("state 1") // different value than the child starts with.
        }
        advanceIfStandard(dispatcher)

        if (runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS)) {
          assertEquals(2, parentRenderCount)
        } else {
          assertEquals(3, parentRenderCount)
        }
        // Parent needs to be rendered 3x, but child only 2x as the 3rd time its the same.
        assertEquals(2, childRenderCount)
      }
    }
  }

  @Test
  fun for_render_on_change_only_and_conflate_we_drain_action_but_do_not_render_no_state_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions.filter {
        it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES) &&
          it.first.contains(CONFLATE_STALE_RENDERINGS)
      },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(CONFLATE_STALE_RENDERINGS))
        check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))

        var renderCount = 0
        var childHandlerActionExecuted = 0
        var workerActionExecuted = 0
        val trigger = MutableSharedFlow<String>()
        val outputSet = mutableListOf<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                childHandlerActionExecuted++
                setOutput(childOutput)
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                workerActionExecuted++
                state = it
                setOutput(it)
              }
            }
            renderState.also {
              renderCount++
            }
          }
        )
        val props = MutableStateFlow(Unit)
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          outputSet.add(it)
          yield()
        }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        // 2 renderings (initial and then the update.) Not *3* renderings.
        assertEquals(2, renderCount)
        assertEquals(1, childHandlerActionExecuted)
        assertEquals(1, workerActionExecuted)
        assertEquals(2, outputSet.size)
        assertEquals("changed state", outputSet[0])
      }
    }
  }

  /**
   * This is the same test as [for_conflate_we_do_not_conflate_stacked_actions_into_one_rendering_if_output]
   * except that in that version the handler for the child output also sets output - which is
   * one reason we do not end up conflating.
   */
  @Test
  fun for_conflate_we_conflate_stacked_actions_into_one_rendering() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(CONFLATE_STALE_RENDERINGS)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(CONFLATE_STALE_RENDERINGS))

        var childHandlerActionExecuted = false
        val trigger = MutableSharedFlow<String>()
        val emitted = mutableListOf<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                childHandlerActionExecuted = true
                state = childOutput
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                // Update the rendering in order to show conflation.
                state = "$it+update"
                setOutput(state)
              }
            }
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          // Yield in output so that we ensure that we let the collector of the renderings
          // collect each of them before processing the next action.
          yield()
        }
        advanceIfStandard(dispatcher)

        val collectionJob = launch {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            emitted += it.rendering
          }
        }
        advanceIfStandard(dispatcher)
        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        collectionJob.cancel()

        // 2 renderings (initial and then the update.) Not *3* renderings.
        assertEquals(2, emitted.size, "Expected only 2 total renderings.")
        assertEquals("changed state+update", emitted.last())
        assertTrue(childHandlerActionExecuted)
      }
    }
  }

  @Test
  fun for_conflate_we_do_not_conflate_stacked_actions_into_one_rendering_if_output() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(CONFLATE_STALE_RENDERINGS)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(CONFLATE_STALE_RENDERINGS))

        var childHandlerActionExecuted = false
        val trigger = MutableSharedFlow<String>()
        val emitted = mutableListOf<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                childHandlerActionExecuted = true
                state = childOutput
                setOutput(childOutput)
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                // Update the rendering in order to show conflation.
                state = "$it+update"
                setOutput("$it+update")
              }
            }
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          // Yield in output so that we ensure that we let the collector of the renderings
          // collect each of them before processing the next action.
          yield()
        }

        val collectionJob = launch {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            emitted += it.rendering
          }
        }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        collectionJob.cancel()

        // 3 renderings because each had output.
        assertEquals(3, emitted.size)
        assertEquals("changed state+update", emitted.last())
        assertTrue(childHandlerActionExecuted)
      }
    }
  }

  @Test
  fun for_conflate_and_render_only_when_state_changed_we_do_not_conflate_stacked_actions_into_one_rendering_if_previous_rendering_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(CONFLATE_STALE_RENDERINGS) &&
            it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(CONFLATE_STALE_RENDERINGS))
        check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))

        var childHandlerActionExecuted = false
        val trigger = MutableSharedFlow<String>()
        val emitted = mutableListOf<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                childHandlerActionExecuted = true
                state = "$childOutput+update"
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                // no state change now!
              }
            }
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { }

        val collectionJob = launch {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            emitted += it.rendering
          }
        }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        collectionJob.cancel()

        // 2 renderings.
        assertEquals(2, emitted.size)
        assertEquals("changed state+update", emitted.last())
        assertTrue(childHandlerActionExecuted)
      }
    }
  }

  @Test
  fun for_conflate_and_render_only_when_state_changed_we_do_not_render_again_if_only_previous_rendering_changed() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(CONFLATE_STALE_RENDERINGS) &&
            it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(CONFLATE_STALE_RENDERINGS))
        check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))

        var childHandlerActionExecuted = false
        val trigger = MutableSharedFlow<String>()
        val emitted = mutableListOf<String>()
        var renderCount = 0

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow) { childOutput ->
              action("childHandler") {
                childHandlerActionExecuted = true
                state = "$childOutput+update"
              }
            }
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                // no state change now!
              }
            }
            renderState.also {
              renderCount++
            }
          }
        )
        val props = MutableStateFlow(Unit)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { }

        val collectionJob = launch {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            emitted += it.rendering
          }
        }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        collectionJob.cancel()

        // 2 renderings.
        assertEquals(2, emitted.size)
        assertEquals("changed state+update", emitted.last())
        // Only 2 times rendered, the initial + the update (not 3).
        assertEquals(2, renderCount)
        assertTrue(childHandlerActionExecuted)
      }
    }
  }

  @Test
  fun for_drain_exclusive_we_handle_multiple_actions_in_one_render_or_not() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {

        var childActionAppliedCount = 0
        var parentRenderCount = 0
        val trigger = MutableSharedFlow<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanged state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
                childActionAppliedCount++
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow, key = "key1") { _ ->
              WorkflowAction.noAction()
            }
            renderChild(childWorkflow, key = "key2") { _ ->
              WorkflowAction.noAction()
            }
            parentRenderCount++
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        // 2 child actions processed.
        assertEquals(2, childActionAppliedCount, "Expecting 2 child actions to be applied.")
        if (runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS)) {
          //  and 2 parent renders - 1 initial (synchronous) and then 1 additional.
          assertEquals(2, parentRenderCount, "Expecting only 2 total renders.")
        } else {
          //  and 3 parent renders - 1 initial (synchronous) and then 1 additional for each child.
          assertEquals(3, parentRenderCount, "Expecting only 3 total renders.")
        }
      }
    }
  }

  @Test
  fun `for_drain_exclusive_and_render_only_when_state_changes_we_handle_multiple_actions_in_one_render_but_do_not_render_if_no_state_change`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(DRAIN_EXCLUSIVE_ACTIONS) &&
            it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS))
        check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))

        var childActionAppliedCount = 0
        var parentRenderCount = 0
        val trigger = MutableSharedFlow<String>()
        val receivedOutputs = mutableListOf<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanged state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                // no state change!
                childActionAppliedCount++
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow, key = "key1") { _ ->
              WorkflowAction.noAction()
            }
            renderChild(childWorkflow, key = "key2") { childOutput ->
              action(name = "Child2Handler") {
                // Second one sets output to test that we still send the output!
                setOutput(childOutput)
              }
            }
            parentRenderCount++
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          receivedOutputs.add(it)
        }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        // 2 child actions processed and 1 parent render - only the initial one.
        assertEquals(2, childActionAppliedCount, "Expected each child action applied.")
        assertEquals(1, parentRenderCount, "Expected parent only rendered once.")
        assertEquals(1, receivedOutputs.size, "Expected one output.")
        assertEquals("changed state", receivedOutputs[0])
      }
    }
  }

  @Test
  fun `for_drain_exclusive_and_render_only_when_state_changes_we_handle_multiple_actions_in_one_render_but_we_do_pass_rendering_if_state_changed_earlier`() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(DRAIN_EXCLUSIVE_ACTIONS) &&
            it.first.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS))
        check(runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES))

        var childActionAppliedCount = 0
        var parentRenderCount = 0
        val trigger = MutableSharedFlow<String>()
        val receivedOutputs = mutableListOf<String>()
        val emitted = mutableListOf<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanged state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                if (childActionAppliedCount == 0) {
                  // change state on the first one.
                  state = "$it+update"
                } else {
                  // no state change!
                }
                childActionAppliedCount++
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow, key = "key1") { _ ->
              WorkflowAction.noAction()
            }
            renderChild(childWorkflow, key = "key2") { childOutput ->
              action(name = "Child2Handler") {
                // Second one sets output to test that we still send the output!
                setOutput(childOutput)
              }
            }
            parentRenderCount++
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) {
          receivedOutputs.add(it)
        }
        advanceIfStandard(dispatcher)

        val collectionJob = launch {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            emitted += it.rendering
          }
        }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        collectionJob.cancel()

        // 2 renderings received always as the state on child changes!
        assertEquals(2, emitted.size)
        // 2 child actions processed and 1 (or 2) parent renders.
        assertEquals(2, childActionAppliedCount, "Expected each child action applied.")
        if (runtimeConfig.contains(PARTIAL_TREE_RENDERING)) {
          assertEquals(1, parentRenderCount, "Expected parent only rendered once.")
        } else {
          assertEquals(2, parentRenderCount, "Expected parent rendered twice.")
        }
        assertEquals(1, receivedOutputs.size, "Expected one output.")
        assertEquals("changed state", receivedOutputs[0])
      }
    }
  }

  @Test
  fun for_drain_exclusive_we_do_not_handle_multiple_actions_in_one_render_if_not_exclusive() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions
        .filter {
          it.first.contains(DRAIN_EXCLUSIVE_ACTIONS)
        },
      before = ::setup,
    ) { (runtimeConfig: RuntimeConfig, workflowTracer: WorkflowTracer?, dispatcher: TestDispatcher) ->
      runTest(dispatcher) {
        check(runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS))

        var childActionAppliedCount = 0
        var parentRenderCount = 0
        val trigger = MutableSharedFlow<String>()

        val childWorkflow = Workflow.stateful<String, String, String>(
          initialState = "unchanged state",
          render = { renderState ->
            runningWorker(
              trigger.asWorker()
            ) {
              action("") {
                state = it
                childActionAppliedCount++
                // set the output to dirty the parent node.
                setOutput(it)
              }
            }
            renderState
          }
        )
        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanging state",
          render = { renderState ->
            renderChild(childWorkflow, key = "key1") { childOutput ->
              action("childHandler1") {
                state = childOutput
              }
            }
            renderChild(childWorkflow, key = "key2") { childOutput ->
              action("childHandler2") {
                state = childOutput
              }
            }
            parentRenderCount++
            renderState
          }
        )
        val props = MutableStateFlow(Unit)
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = workflowTracer,
        ) { }
        advanceIfStandard(dispatcher)

        launch {
          trigger.emit("changed state")
        }
        advanceIfStandard(dispatcher)

        // 2 child actions processed and 3 parent renders
        assertEquals(2, childActionAppliedCount, "Expecting 2 child actions applied")
        assertEquals(3, parentRenderCount, "Expecting 3 parent renders")
      }
    }
  }

  private class ExpectedException : RuntimeException()

  private fun <T1, T2> cartesianProduct(
    set1: Sequence<T1>,
    set2: Sequence<T2>
  ): Sequence<Pair<T1, T2>> {
    return set1.flatMap { set1Item -> set2.map { set2Item -> set1Item to set2Item } }
  }

  private fun <T1, T2, T3> cartesianProduct(
    set1: Sequence<T1>,
    set2: Sequence<T2>,
    set3: Sequence<T3>
  ): Sequence<Triple<T1, T2, T3>> {
    return set1.flatMap { set1Item -> set2.map { set2Item -> set1Item to set2Item } }
      .flatMap { (set1Item, set2Item) ->
        set3.map { set3Item ->
          Triple(
            set1Item,
            set2Item,
            set3Item
          )
        }
      }
  }

  companion object {
    internal val EXPECTED_TRACE: String = """
StartingCreateWorkerWorkflow
Ending
StartingCheckingUniqueMatches
Ending
StartingRetainingChildren
Ending
StartingCreateSideEffectNode
Ending
StartingUpdateRuntimeTree
Ending
StartingUpdateRuntimeTree
Ending
StartingCreateWorkerWorkflow
Ending
StartingCheckingUniqueMatches
Ending
StartingRetainingChildren
  Startingmatches
  Ending
Ending
StartingUpdateRuntimeTree
Ending
StartingUpdateRuntimeTree
Ending
    """.trim()
  }
}
