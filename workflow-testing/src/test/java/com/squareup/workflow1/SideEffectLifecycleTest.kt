package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.testing.headlessIntegrationTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.coroutineContext
import kotlin.test.Test

@OptIn(WorkflowExperimentalRuntime::class)
class SideEffectLifecycleTest {

  private val runtimeOptions: Sequence<RuntimeConfig> = arrayOf(
    RuntimeConfigOptions.RENDER_PER_ACTION,
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(CONFLATE_STALE_RENDERINGS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
  ).asSequence()

  private val runtimeTestRunner = ParameterizedTestRunner<RuntimeConfig>()
  private var started = 0
  private var cancelled = 0
  private val workflow: StatefulWorkflow<Unit, Int, Nothing, Pair<Int, (Int) -> Unit>> =
    Workflow.stateful(
      initialState = 0,
      render = { renderState: Int ->
        // Run side effect on odd numbered state.
        if (renderState % 2 == 1) {
          runningSideEffect("test") {
            started++
            try {
              // actionSink.send(action { state = 0 })
              awaitCancellation()
            } finally {
              cancelled++
            }
          }
        }
        // Rendering pair is current int state and a function to change it.
        Pair(
          renderState,
          { newState -> actionSink.send(action { state = newState }) }
        )
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

      workflow.headlessIntegrationTest(
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

      workflow.headlessIntegrationTest(
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
   * @see https://github.com/square/workflow-kotlin/issues/1093
   */
  @Test fun sideEffectsStartAndStoppedWhenHandledSynchronously() {
    runtimeTestRunner.runParametrizedTest(
      paramSource = runtimeOptions,
      after = ::cleanup,
    ) { runtimeConfig: RuntimeConfig ->

      val dispatcher = StandardTestDispatcher()
      workflow.headlessIntegrationTest(
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
}
