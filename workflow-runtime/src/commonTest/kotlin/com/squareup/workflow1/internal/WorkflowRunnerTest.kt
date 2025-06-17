package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateful
import com.squareup.workflow1.stateless
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
internal class WorkflowRunnerTest {

  private lateinit var scope: TestScope

  private val runtimeOptions = arrayOf(
    RuntimeConfigOptions.RENDER_PER_ACTION,
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(CONFLATE_STALE_RENDERINGS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
  ).asSequence()

  private fun setup() {
    scope = TestScope()
  }

  private fun tearDown() {
    scope.cancel()
  }

  private val runtimeTestRunner = ParameterizedTestRunner<RuntimeConfig>()

  @Test fun initial_nextRendering_returns_initial_rendering() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateless<Unit, Nothing, String> { "foo" }
      val runner = WorkflowRunner(
        workflow,
        MutableStateFlow(Unit),
        runtimeConfig
      )
      val rendering = runner.nextRendering().rendering
      assertEquals("foo", rendering)
    }
  }

  @Test fun initial_nextRendering_uses_initial_props() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateless<String, Nothing, String> { it }
      val runner = WorkflowRunner(
        workflow,
        MutableStateFlow("foo"),
        runtimeConfig
      )
      val rendering = runner.nextRendering().rendering
      assertEquals("foo", rendering)
    }
  }

  @Test fun initial_waitForActions_does_not_handle_initial_props() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateless<String, Nothing, String> { it }
      val props = MutableStateFlow("initial")
      val runner = WorkflowRunner(
        workflow,
        props,
        runtimeConfig
      )
      runner.nextRendering()

      val outputDeferred = scope.async { runner.waitForAction() }

      scope.runCurrent()
      assertTrue(outputDeferred.isActive)
    }
  }

  @Test fun initial_waitForActions_handles_props_changed_after_initialization() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateless<String, Nothing, String> { it }
      val props = MutableStateFlow("initial")
      // The dispatcher is paused, so the produceIn coroutine won't start yet.
      val runner = WorkflowRunner(
        workflow,
        props,
        runtimeConfig
      )
      // The initial value will be read during initialization, so we can change it any time after
      // that.
      props.value = "changed"

      // Get the runner into the state where it's waiting for a props update.
      val initialRendering = runner.nextRendering().rendering
      assertEquals("initial", initialRendering)
      val output = scope.async { runner.waitForAction() }
      assertTrue(output.isActive)

      // Resume the dispatcher to start the coroutines and process the new props value.
      scope.runCurrent()

      assertTrue(output.isCompleted)
      @Suppress("UNCHECKED_CAST")
      val outputValue = output.getCompleted() as? ActionApplied<String>?
      assertNull(outputValue)
      val rendering = runner.nextRendering().rendering
      assertEquals("changed", rendering)
    }
  }

  @Test fun waitForActions_handles_workflow_update() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateful<Unit, String, String, String>(
        initialState = { "initial" },
        render = { _, renderState ->
          runningWorker(Worker.from { "work" }) {
            action("") {
              state = "state: $it"
              setOutput("output: $it")
            }
          }
          return@stateful renderState
        }
      )
      val runner =
        WorkflowRunner(workflow, MutableStateFlow(Unit), runtimeConfig)

      val initialRendering = runner.nextRendering().rendering
      assertEquals("initial", initialRendering)

      val actionResult = runner.runTillNextActionResult()
      assertEquals("output: work", actionResult!!.output!!.value)

      val updatedRendering = runner.nextRendering().rendering
      assertEquals("state: work", updatedRendering)
    }
  }

  @Test fun waitForActions_handles_concurrent_props_change_and_workflow_update() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateful<String, String, String, String>(
        initialState = { "initial state($it)" },
        render = { renderProps, renderState ->
          runningWorker(Worker.from { "work" }) {
            action("") {
              state = "state: $it"
              setOutput("output: $it")
            }
          }
          return@stateful "$renderProps|$renderState"
        }
      )
      val props = MutableStateFlow("initial props")
      val runner = WorkflowRunner(workflow, props, runtimeConfig)
      props.value = "changed props"
      val initialRendering = runner.nextRendering().rendering
      assertEquals("initial props|initial state(initial props)", initialRendering)

      // The order in which props update and workflow update are processed is deterministic, based
      // on the order they appear in the select block in processActions.
      val firstActionResult = runner.runTillNextActionResult()
      // First update will be props, so no output value.
      assertNull(firstActionResult)
      val secondRendering = runner.nextRendering().rendering
      assertEquals("changed props|initial state(initial props)", secondRendering)

      val secondActionResult = runner.runTillNextActionResult()
      assertEquals("output: work", secondActionResult!!.output!!.value)
      val thirdRendering = runner.nextRendering().rendering
      assertEquals("changed props|state: work", thirdRendering)
    }
  }

  @Test fun cancelRuntime_does_not_interrupt_waitForActions() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
      val runner =
        WorkflowRunner(workflow, MutableStateFlow(Unit), runtimeConfig)
      runner.nextRendering()
      val output = scope.async { runner.waitForAction() }
      scope.runCurrent()
      assertTrue(output.isActive)

      // processActions is run on the scope passed to the runner, so it shouldn't be affected by this
      // call.
      runner.cancelRuntime()

      scope.advanceUntilIdle()
      assertTrue(output.isActive)
    }
  }

  @Test fun cancelRuntime_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      var cancellationException: Throwable? = null
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect(key = "test side effect") {
          suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cause -> cancellationException = cause }
          }
        }
      }
      val runner =
        WorkflowRunner(workflow, MutableStateFlow(Unit), runtimeConfig)
      runner.nextRendering()
      scope.runCurrent()
      assertNull(cancellationException)

      runner.cancelRuntime()

      scope.advanceUntilIdle()
      assertNotNull(cancellationException)
      val causes = generateSequence(cancellationException) { it.cause }
      assertTrue(causes.all { it is CancellationException })
    }
  }

  @Test fun cancelling_scope_interrupts_waitForActions() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      val workflow = Workflow.stateless<Unit, Nothing, Unit> {}
      val runner =
        WorkflowRunner(workflow, MutableStateFlow(Unit), runtimeConfig)
      runner.nextRendering()
      val actionResult = scope.async { runner.waitForAction() }
      scope.runCurrent()
      assertTrue(actionResult.isActive)

      scope.cancel("foo")

      scope.advanceUntilIdle()
      assertTrue(actionResult.isCancelled)
      val realCause = actionResult.getCompletionExceptionOrNull()
      assertEquals("foo", realCause?.message)
    }
  }

  @Test fun cancelling_scope_cancels_runtime() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      before = ::setup,
      after = ::tearDown,
    ) { runtimeConfig: RuntimeConfig ->

      var cancellationException: Throwable? = null
      val workflow = Workflow.stateless<Unit, Nothing, Unit> {
        runningSideEffect(key = "test") {
          suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cause -> cancellationException = cause }
          }
        }
      }
      val runner =
        WorkflowRunner(workflow, MutableStateFlow(Unit), runtimeConfig)
      runner.nextRendering()
      val actionResult = scope.async { runner.waitForAction() }
      scope.runCurrent()
      assertTrue(actionResult.isActive)
      assertNull(cancellationException)

      scope.cancel("foo")

      scope.advanceUntilIdle()
      assertTrue(actionResult.isCancelled)
      assertNotNull(cancellationException)
      assertEquals("foo", cancellationException!!.message)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> WorkflowRunner<*, T, *>.runTillNextActionResult(): ActionApplied<T>? = scope.run {
    val firstOutputDeferred = async { waitForAction() }
    runCurrent()
    // If it is [ PropsUpdated] or any other ActionProcessingResult, will return as null.
    val actionResult = firstOutputDeferred.getCompleted() as? ActionApplied<T>
    return@run actionResult
  }

  @Suppress("TestFunctionName")
  private fun <P, O : Any, R> WorkflowRunner(
    workflow: Workflow<P, O, R>,
    props: StateFlow<P>,
    runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG
  ): WorkflowRunner<P, O, R> = WorkflowRunner(
    scope,
    workflow,
    props,
    snapshot = null,
    interceptor = NoopWorkflowInterceptor,
    runtimeConfig,
    workflowTracer = null
  )
}
