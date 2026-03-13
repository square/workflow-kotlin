@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.squareup.workflow1.testing

import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateful
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests verifying the [DeprecatedLaunchSchedulerMode] migration bridge for the deprecated
 * `launchForTesting*` APIs.
 *
 * These tests lock the semantics of each mode to prevent regressions during phased migration.
 */
internal class DeprecatedLaunchSchedulerModeTest {

  // -- LEGACY_UNCONFINED mode tests --

  @Test fun `legacy mode - side effects are immediately observable`() {
    var renderCount = 0
    val workflow = Workflow.stateful<Unit, String, Nothing, () -> Unit>(
      initialState = { "initial" },
      render = { _, _ ->
        renderCount++
        eventHandler("update") {
          state = "updated"
        }
      }
    )

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.LEGACY_UNCONFINED
      )
    ) {
      val onUpdate = awaitNextRendering()
      val countBeforeAction = renderCount
      // Invoke the event handler — with UnconfinedTestDispatcher the action dispatches
      // immediately, so the render count increases synchronously without advanceUntilSettled().
      onUpdate()
      assertTrue(renderCount > countBeforeAction)
    }
  }

  @Test fun `legacy mode - delay worker does not auto-complete`() {
    val workflow = Workflow.stateful<Unit, String, String, Unit>(
      initialState = { "waiting" },
      render = { _, _ ->
        runningWorker(
          Worker.from {
            delay(5_000)
            "done"
          }
        ) {
          action("workerResult") { setOutput(it) }
        }
      }
    )

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.LEGACY_UNCONFINED
      )
    ) {
      // The delay-based worker should NOT have completed — virtual time is not auto-advanced.
      assertFailsWith<TimeoutCancellationException> {
        awaitNextOutput(timeoutMs = 100)
      }
    }
  }

  // -- VIRTUAL_TIME_STANDARD mode tests --

  @Test fun `virtual time mode - side effects require advanceUntilSettled`() {
    var renderCount = 0
    val workflow = Workflow.stateful<Unit, String, Nothing, () -> Unit>(
      initialState = { "initial" },
      render = { _, _ ->
        renderCount++
        eventHandler("update") {
          state = "updated"
        }
      }
    )

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD
      )
    ) {
      val onUpdate = awaitNextRendering()
      val countBeforeAction = renderCount
      onUpdate()
      // With StandardTestDispatcher, the action is queued but not yet processed.
      assertEquals(countBeforeAction, renderCount)
      // advanceUntilSettled() processes the queued action and triggers a new render.
      advanceUntilSettled()
      assertTrue(renderCount > countBeforeAction)
      assertTrue(hasRendering)
    }
  }

  @Test fun `virtual time mode - delay worker completes with advance`() {
    val workflow = Workflow.stateful<Unit, String, String, Unit>(
      initialState = { "waiting" },
      render = { _, _ ->
        runningWorker(
          Worker.from {
            delay(5_000)
            "done"
          }
        ) {
          action("workerResult") { setOutput(it) }
        }
      }
    )

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD
      )
    ) {
      // After advancing (done internally by awaitNextOutput -> advanceUntilSettled),
      // the delay completes and the worker emits output.
      val output = awaitNextOutput()
      assertEquals("done", output)
    }
  }

  @Test fun `virtual time mode - startup auto-advance is enabled by default`() {
    val workflow = startupAdvanceWorkflow()

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD
      )
    ) {
      assertEquals("installing", awaitNextRendering())
    }
  }

  @Test fun `virtual time mode - startup auto-advance can be disabled`() {
    val workflow = startupAdvanceWorkflow()

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD,
        autoAdvanceOnStartup = false
      )
    ) {
      assertEquals("prompt", awaitNextRendering())
      advanceUntilSettled()
      assertEquals("installing", awaitNextRendering())
    }
  }

  @Test fun `virtual time mode - awaitNextRendering can skip scheduler advancement`() {
    val workflow = startupAdvanceWorkflow()

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD,
        autoAdvanceOnStartup = false,
        autoAdvanceBeforeAwait = false
      )
    ) {
      assertEquals("prompt", awaitNextRendering(advanceScheduler = false))

      val timedOut = runCatching {
        awaitNextRendering(timeoutMs = 100, advanceScheduler = false)
      }
      assertTrue(timedOut.isFailure)

      assertEquals("installing", awaitNextRendering(advanceScheduler = true))
    }
  }

  @Test fun `virtual time mode - awaitNextOutput can skip scheduler advancement`() {
    val workflow = Workflow.stateful<Unit, String, String, String>(
      initialState = { "waiting" },
      render = { _, state ->
        runningWorker(
          Worker.from {
            delay(5_000)
            "done"
          }
        ) {
          action("workerResult") { setOutput(it) }
        }
        state
      }
    )

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD,
        autoAdvanceOnStartup = false,
        autoAdvanceBeforeAwait = false
      )
    ) {
      assertEquals("waiting", awaitNextRendering(advanceScheduler = false))

      val timedOut = runCatching {
        awaitNextOutput(timeoutMs = 100, advanceScheduler = false)
      }
      assertTrue(timedOut.isFailure)

      assertEquals("done", awaitNextOutput(advanceScheduler = true))
    }
  }

  @Test fun `virtual time mode - hasRendering does not auto-advance when disabled`() {
    val workflow = startupAdvanceWorkflow()

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD,
        autoAdvanceOnStartup = false,
        autoAdvanceBeforeHasCheck = false
      )
    ) {
      assertEquals("prompt", awaitNextRendering(advanceScheduler = false))
      assertFalse(hasRendering)

      advanceUntilSettled()
      assertTrue(hasRendering)
      assertEquals("installing", awaitNextRendering(advanceScheduler = false))
    }
  }

  @Test fun `virtual time mode - hasOutput does not auto-advance when disabled`() {
    val workflow = Workflow.stateful<Unit, Unit, String, Unit>(
      initialState = { Unit },
      render = { _, _ ->
        runningWorker(
          Worker.from {
            delay(5_000)
            "done"
          }
        ) {
          action("workerResult") { setOutput(it) }
        }
      }
    )

    workflow.launchForTestingFromStartWith(
      testParams = WorkflowTestParams(
        checkRenderIdempotence = false,
        deprecatedLaunchSchedulerMode = DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD,
        autoAdvanceOnStartup = false,
        autoAdvanceBeforeHasCheck = false
      )
    ) {
      awaitNextRendering(advanceScheduler = false)
      assertFalse(hasOutput)

      advanceUntilSettled()
      assertTrue(hasOutput)
      assertEquals("done", awaitNextOutput(advanceScheduler = false))
    }
  }

  private fun startupAdvanceWorkflow() = Workflow.stateful<Unit, String, Nothing, String>(
    initialState = { "prompt" },
    render = { _, state ->
      if (state == "prompt") {
        runningWorker(
          Worker.from {
            delay(5_000)
            "installing"
          }
        ) {
          action("timerFinished") {
            this.state = it
          }
        }
      }
      state
    }
  )
}
