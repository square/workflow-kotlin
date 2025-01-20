package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.testing.headlessIntegrationTest
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Most of these tests are motivated by [1093](https://github.com/square/workflow-kotlin/issues/1093).
 */
@OptIn(WorkflowExperimentalRuntime::class, WorkflowExperimentalApi::class)
class WorkflowsLifecycleTests {

  private val runtimeOptions: Sequence<RuntimeConfig> = arrayOf(
    RuntimeConfigOptions.RENDER_PER_ACTION,
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(CONFLATE_STALE_RENDERINGS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
  ).asSequence()

  private val runtimeTestRunner = ParameterizedTestRunner<RuntimeConfig>()
  private var started = 0
  private var cancelled = 0
  private val workflowWithSideEffects:
    StatefulWorkflow<Unit, Int, Nothing, Pair<Int, (Int) -> Unit>> =
    Workflow.stateful(
      initialState = 0,
      render = { renderState: Int ->
        // Run side effect on odd numbered state.
        if (renderState % 2 == 1) {
          runningSideEffect("test") {
            started++
            try {
              awaitCancellation()
            } finally {
              cancelled++
            }
          }
        }
        // Rendering pair is current int state and a function to change it.
        renderState to { newState -> actionSink.send(action("") { state = newState }) }
      }
    )

  private val sessionWorkflow: SessionWorkflow<Unit, Int, Nothing, Int> =
    Workflow.sessionWorkflow(
      initialState = { workflowScope ->
        workflowScope.coroutineContext[Job]!!.invokeOnCompletion {
          cancelled++
        }
        started++
        0
      },
      render = { renderState: Int ->
        renderState
      }
    )

  private val workflowWithChildSession:
    StatefulWorkflow<Unit, Int, Nothing, Pair<Int, (Int) -> Unit>> =
    Workflow.stateful(
      initialState = 0,
      render = { renderState: Int ->
        // render child session on odd numbered state.
        if (renderState % 2 == 1) {
          renderChild(sessionWorkflow)
        }
        // Rendering pair is current int state and a function to change it.
        renderState to { newState -> actionSink.send(action("") { state = newState }) }
      }
    )

  private fun cleanup() {
    started = 0
    cancelled = 0
  }

  @Test fun sideEffectsStartedWhenExpected() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      workflowWithSideEffects.headlessIntegrationTest(
        runtimeConfig = runtimeConfig
      ) {
        // One time starts but does not stop the side effect.
        repeat(1) {
          val (current, setState) = awaitNextRendering()
          setState.invoke(current + 1)
        }

        assertEquals(1, started, "Side Effect not started 1 time.")
      }
    }
  }

  @Test fun sideEffectsStoppedWhenExpected() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      workflowWithSideEffects.headlessIntegrationTest(
        runtimeConfig = runtimeConfig
      ) {
        // Twice will start and stop the side effect.
        repeat(2) {
          val (current, setState) = awaitNextRendering()
          setState.invoke(current + 1)
        }
        assertEquals(1, started, "Side Effect not started 1 time.")
        assertEquals(1, cancelled, "Side Effect not cancelled 1 time.")
      }
    }
  }

  /**
   * @see [1093](https://github.com/square/workflow-kotlin/issues/1093)
   *
   * This test ensconces the currently failing behavior of side effects. We are not currently
   * fixing this but rather working around it with [SessionWorkflow].
   */
  @Ignore
  @Test fun sideEffectsStartAndStoppedWhenHandledSynchronously() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      val dispatcher = StandardTestDispatcher()
      workflowWithSideEffects.headlessIntegrationTest(
        coroutineContext = dispatcher,
        runtimeConfig = runtimeConfig
      ) {

        val (_, setState) = awaitNextRendering()
        // 2 actions queued up - should start the side effect and then stop it
        // on two consecutive render passes.
        setState.invoke(1)
        setState.invoke(2)
        dispatcher.scheduler.runCurrent()
        awaitNextRendering()
        dispatcher.scheduler.runCurrent()
        if (!runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) {
          // 2 rendering or 1 depending on runtime config.
          awaitNextRendering()
        }

        assertEquals(1, started, "Side Effect not started 1 time.")
        assertEquals(1, cancelled, "Side Effect not cancelled 1 time.")
      }
    }
  }

  @Test fun childSessionWorkflowStartedWhenExpected() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      workflowWithChildSession.headlessIntegrationTest(
        runtimeConfig = runtimeConfig
      ) {
        // One time starts but does not stop the child session workflow.
        repeat(1) {
          val (current, setState) = awaitNextRendering()
          setState.invoke(current + 1)
        }

        assertEquals(1, started, "Child Session Workflow not started 1 time.")
      }
    }
  }

  @Test fun childSessionWorkflowStoppedWhenExpected() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      workflowWithChildSession.headlessIntegrationTest(
        runtimeConfig = runtimeConfig
      ) {
        // Twice will start and stop the child session workflow.
        repeat(2) {
          val (current, setState) = awaitNextRendering()
          setState.invoke(current + 1)
        }
        assertEquals(1, started, "Child Session Workflow not started 1 time.")
        assertEquals(1, cancelled, "Child Session Workflow not cancelled 1 time.")
      }
    }
  }

  /**
   * @see [1093](https://github.com/square/workflow-kotlin/issues/1093)
   *
   * This tests show the working behavior when using a [SessionWorkflow] to track the lifetime.
   */
  @Test fun childSessionWorkflowStartAndStoppedWhenHandledSynchronously() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      val dispatcher = StandardTestDispatcher()
      workflowWithChildSession.headlessIntegrationTest(
        coroutineContext = dispatcher,
        runtimeConfig = runtimeConfig
      ) {

        val (_, setState) = awaitNextRendering()
        // 2 actions queued up - should start the child session workflow and then stop it
        // on two consecutive render passes, synchronously.
        setState.invoke(1)
        setState.invoke(2)
        dispatcher.scheduler.runCurrent()
        awaitNextRendering()
        dispatcher.scheduler.runCurrent()
        if (!runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) {
          // 2 rendering or 1 depending on runtime config.
          awaitNextRendering()
        }

        assertEquals(1, started, "Child Session Workflow not started 1 time.")
        assertEquals(1, cancelled, "Child Session Workflow not cancelled 1 time.")
      }
    }
  }
}
