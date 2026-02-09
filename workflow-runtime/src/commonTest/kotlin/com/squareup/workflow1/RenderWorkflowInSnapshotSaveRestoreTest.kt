package com.squareup.workflow1

import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.NONE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This only contains the single test ([saves_to_and_restores_from_snapshot]) from
 * [RenderWorkflowInTest] that uses [runtime2], since in Kotlin 2.3.10 passing [runtime2] as a test
 * method parameter causes a compiler crash. It should be possible to merge this test back into the
 * main suite once that bug is fixed.
 */
@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
@Burst
class RenderWorkflowInSnapshotSaveRestoreTest(
  useTracer: Boolean = false,
  private val useUnconfined: Boolean = true,
  private val runtime: RuntimeOptions = NONE,
  private val runtime2: RuntimeOptions = NONE,
) {

  private val runtimeConfig = runtime.runtimeConfig
  private val traces: StringBuilder = StringBuilder()
  private val testTracer: WorkflowTracer? = if (useTracer) {
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
  }

  @Test fun saves_to_and_restores_from_snapshot() = runTest(dispatcherUsed) {
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

    val snapshot = renderings.value.let { (rendering, snapshot) ->
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
        runtimeConfig = runtime2.runtimeConfig
      ) {}
    advanceIfStandard()
    assertEquals(
      "updated state",
      restoredRenderings.value.rendering.first
    )
  }
}
