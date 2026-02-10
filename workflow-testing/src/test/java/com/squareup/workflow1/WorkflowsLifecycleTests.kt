@file:Suppress("JUnitMalformedDeclaration")

package com.squareup.workflow1

import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.NONE
import com.squareup.workflow1.RuntimeConfigOptions.DRAIN_EXCLUSIVE_ACTIONS
import com.squareup.workflow1.testing.WorkflowTestParams
import com.squareup.workflow1.testing.renderForTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Most of these tests are motivated by [1093](https://github.com/square/workflow-kotlin/issues/1093).
 */
@OptIn(WorkflowExperimentalRuntime::class, WorkflowExperimentalApi::class)
@Burst
class WorkflowsLifecycleTests(
  private val runtime: RuntimeOptions = NONE
) {

  private val runtimeConfig = runtime.runtimeConfig

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
    workflowWithSideEffects.renderForTest(
      testParams = WorkflowTestParams(
        runtimeConfig = runtimeConfig
      )
    ) {
      // One time starts but does not stop the side effect.
      repeat(1) {
        val (current, setState) = awaitNextRendering()
        setState.invoke(current + 1)
      }

      // Advance to process the queued action and render pass before asserting.
      advanceRuntime()
      assertEquals(1, started, "Side Effect not started 1 time.")
    }
  }

  @Test fun sideEffectsStoppedWhenExpected() {
    workflowWithSideEffects.renderForTest(
      testParams = WorkflowTestParams(
        runtimeConfig = runtimeConfig
      )
    ) {
      // Twice will start and stop the side effect.
      repeat(2) {
        val (current, setState) = awaitNextRendering()
        setState.invoke(current + 1)
      }
      // Advance to process the final queued action and render pass before asserting.
      advanceRuntime()
      assertEquals(1, started, "Side Effect not started 1 time.")
      assertEquals(1, cancelled, "Side Effect not cancelled 1 time.")
    }
  }

  @Test fun childSessionWorkflowStartedWhenExpected() {
    workflowWithChildSession.renderForTest(
      testParams = WorkflowTestParams(
        runtimeConfig = runtimeConfig
      )
    ) {
      // One time starts but does not stop the child session workflow.
      repeat(1) {
        val (current, setState) = awaitNextRendering()
        setState.invoke(current + 1)
      }

      // Advance to process the queued action and render pass before asserting.
      advanceRuntime()
      assertEquals(1, started, "Child Session Workflow not started 1 time.")
    }
  }

  /**
   * @see [1093](https://github.com/square/workflow-kotlin/issues/1093)
   *
   * This test fails. It is kept and Ignored as a way to ensconce the currently failing behavior
   * of side effects with immediate start & stops. We are not currently fixing this but rather
   * working around it with [SessionWorkflow].
   *
   * Compare with [childSessionWorkflowStartAndStoppedWhenHandledSynchronously]
   */
  @Ignore("https://github.com/square/workflow-kotlin/issues/1093")
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun sideEffectsStartAndStoppedWhenHandledSynchronously() {
    val dispatcher = UnconfinedTestDispatcher()
    workflowWithSideEffects.renderForTest(
      coroutineContext = dispatcher,
      testParams = WorkflowTestParams(
        runtimeConfig = runtimeConfig
      )
    ) {

      val (_, setState) = awaitNextRendering()
      // 2 actions queued up - should start the side effect and then stop it
      // on two consecutive render passes.
      setState.invoke(1)
      setState.invoke(2)
      awaitNextRendering()
      if (!runtimeConfig.contains(CONFLATE_STALE_RENDERINGS) &&
        !runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS)
      ) {
        // 2 rendering or 1 depending on runtime config.
        awaitNextRendering()
      }

      assertEquals(1, started, "Side Effect not started 1 time.")
      assertEquals(1, cancelled, "Side Effect not cancelled 1 time.")
    }
  }

  @Test fun childSessionWorkflowStoppedWhenExpected() {
    workflowWithChildSession.renderForTest(
      testParams = WorkflowTestParams(
        runtimeConfig = runtimeConfig
      )
    ) {
      // Twice will start and stop the child session workflow.
      repeat(2) {
        val (current, setState) = awaitNextRendering()
        setState.invoke(current + 1)
      }
      // Advance to process the final queued action and render pass before asserting.
      advanceRuntime()
      assertEquals(1, started, "Child Session Workflow not started 1 time.")
      assertEquals(1, cancelled, "Child Session Workflow not cancelled 1 time.")
    }
  }

  /**
   * @see [1093](https://github.com/square/workflow-kotlin/issues/1093)
   *
   * This tests show the working behavior when using a [SessionWorkflow] to track the lifetime.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun childSessionWorkflowStartAndStoppedWhenHandledSynchronously() {
    val dispatcher = UnconfinedTestDispatcher()
    workflowWithChildSession.renderForTest(
      coroutineContext = dispatcher,
      testParams = WorkflowTestParams(
        runtimeConfig = runtimeConfig
      )
    ) {

      val (_, setState) = awaitNextRendering()
      // 2 actions queued up - should start the child session workflow and then stop it
      // on two consecutive render passes, synchronously.
      setState.invoke(1)
      setState.invoke(2)
      awaitNextRendering()
      if (!runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) {
        // 2 rendering or 1 depending on runtime config.
        awaitNextRendering()
      }

      assertEquals(1, started, "Child Session Workflow not started 1 time.")
      assertEquals(1, cancelled, "Child Session Workflow not cancelled 1 time.")
    }
  }
}
