package com.squareup.workflow1

import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.NONE
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * This only contains the single test ([saves_to_and_restores_from_snapshot]) from
 * [RenderWorkflowInTest] that needs a second runtime config parameter (`runtime2`). It was split
 * out because Kotlin 2.3.10 crashed when `runtime2` was a test method parameter. Kotlin 2.4.0 still
 * crashes with a `NativeCodeGeneratorException` if the Burst-generated zero-arg specialization has
 * to evaluate `this` in an argument expression (e.g. `runTest(dispatcherUsed) { … }`), so the test
 * body must be reached through a plain call on `this` like [runTestIfConfigValid]. With that
 * workaround it may be possible to merge this test back into the main suite.
 *
 * `runtime2` MUST stay a test method parameter rather than a class parameter. Burst generates a
 * class per combination of class parameters, and Kotlin/Native's generated test registration code
 * for a file reserves stack space for every test class in that file (about 32 bytes per class). As
 * a class parameter this file expands to 2 × 2 × N² classes, where N is the number of
 * [RuntimeOptions] entries, which overflows the 512KB stack of the worker threads that
 * Kotlin/Native runs module initializers on once N passes ~64, crashing the whole test binary with
 * SIGBUS.
 */
@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
@Burst
class RenderWorkflowInSnapshotSaveRestoreTest(
  useTracer: Boolean = false,
  private val useUnconfined: Boolean = true,
  private val runtime: RuntimeOptions = NONE,
) {

  private val runtimeConfig = runtime.runtimeConfig
  private val traces: StringBuilder = StringBuilder()
  private val testTracer: WorkflowTracer? =
    if (useTracer) {
      object : WorkflowTracer {
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
    } else {
      null
    }

  private val myStandardTestDispatcher = StandardTestDispatcher()
  private val dispatcherUsed =
    if (useUnconfined) UnconfinedTestDispatcher() else myStandardTestDispatcher

  private fun advanceIfStandard() {
    if (dispatcherUsed == myStandardTestDispatcher) {
      dispatcherUsed.scheduler.advanceUntilIdle()
      dispatcherUsed.scheduler.runCurrent()
    }
  }

  @BeforeTest
  public fun setup() {
    traces.clear()
    Dispatchers.setMain(dispatcherUsed)
  }

  @AfterTest
  public fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun saves_to_and_restores_from_snapshot(runtime2: RuntimeOptions = NONE) =
    runTestIfConfigValid(runtime2) {
      val workflow =
        Workflow.stateful<Unit, String, Nothing, Pair<String, (String) -> Unit>>(
          initialState = { _, snapshot ->
            snapshot?.bytes?.parse { it.readUtf8WithLength() } ?: "initial state"
          },
          snapshot = { state -> Snapshot.write { it.writeUtf8WithLength(state) } },
          render = { _, renderState ->
            Pair(renderState, { newState -> actionSink.send(action("") { state = newState }) })
          },
        )
      val props = MutableStateFlow(Unit)
      val renderings =
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
        ) {}
      advanceIfStandard()

      // Interact with the workflow to change the state.
      renderings.value.rendering.let { (state, updateState) ->
        assertEquals("initial state", state)
        updateState("updated state")
      }
      advanceIfStandard()

      val snapshot =
        renderings.value.let { (rendering, snapshot) ->
          val (state, updateState) = rendering
          assertEquals("updated state", state)
          updateState("ignored rendering")
          return@let snapshot
        }
      advanceIfStandard()

      // Create a new scope to launch a second runtime to restore.
      val restoreScope = TestScope(dispatcherUsed)
      val restoredRenderings =
        renderWorkflowIn(
          workflow = workflow,
          scope = restoreScope,
          props = props,
          initialSnapshot = snapshot,
          workflowTracer = null,
          runtimeConfig = runtime2.runtimeConfig,
        ) {}
      advanceIfStandard()
      assertEquals("updated state", restoredRenderings.value.rendering.first)
    }

  private fun runTestIfConfigValid(
    runtime2: RuntimeOptions,
    testBody: suspend TestScope.() -> Unit,
  ) {
    if ((COMPOSE_RUNTIME in runtimeConfig) != (COMPOSE_RUNTIME in runtime2.runtimeConfig)) {
      // Snapshots created by the traditional runtime and the compose runtime are not compatible.
      return
    }
    runTest(dispatcherUsed, testBody = testBody)
  }
}
