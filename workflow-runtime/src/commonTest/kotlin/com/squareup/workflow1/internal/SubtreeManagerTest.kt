@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowLocal
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.action
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.identifier
import com.squareup.workflow1.internal.SubtreeManagerTest.TestWorkflow.Rendering
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private typealias StringHandler = (String) -> WorkflowAction<String, String, String>

@ExperimentalCoroutinesApi
internal class SubtreeManagerTest {

  private class TestWorkflow : StatefulWorkflow<String, String, String, Rendering>() {

    var started = 0

    data class Rendering(
      val props: String,
      val state: String,
      val eventHandler: (String) -> Unit
    )

    override fun initialState(
      props: String,
      snapshot: Snapshot?,
      workflowLocal: WorkflowLocal
    ): String {
      started++
      return "initialState:$props"
    }

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext
    ): Rendering {
      return Rendering(
        renderProps,
        renderState,
        eventHandler = context.eventHandler { out -> setOutput("workflow output:$out") }
      )
    }

    override fun snapshotState(state: String) = fail()
  }

  private class SnapshotTestWorkflow : StatefulWorkflow<Unit, Unit, Nothing, Unit>() {

    var snapshots = 0
    var serializes = 0
    var restores = 0

    override fun initialState(
      props: Unit,
      snapshot: Snapshot?,
      workflowLocal: WorkflowLocal
    ) {
      if (snapshot != null) restores++
    }

    override fun render(
      renderProps: Unit,
      renderState: Unit,
      context: RenderContext
    ) {
    }

    override fun snapshotState(state: Unit): Snapshot {
      snapshots++
      return Snapshot.write {
        it.writeByte(0)
        serializes++
      }
    }
  }

  private val context = Unconfined

  @Test fun render_starts_new_child() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()

    manager.render(workflow, "props", key = "", handler = { fail() })
    assertEquals(1, workflow.started)
  }

  @Test fun render_does_not_start_existing_child() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    fun render() = manager.render(workflow, "props", key = "", handler = { fail() })
      .also { manager.commitRenderedChildren() }

    render()
    render()

    assertEquals(1, workflow.started)
  }

  @Test fun render_restarts_child_after_tearing_down() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    fun render() = manager.render(workflow, "props", key = "", handler = { fail() })
      .also { manager.commitRenderedChildren() }
    render()
    assertEquals(1, workflow.started)

    // Render without rendering child.
    manager.commitRenderedChildren()
    assertEquals(1, workflow.started)

    render()
    assertEquals(2, workflow.started)
  }

  @Test fun render_throws_on_duplicate_key() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    manager.render(workflow, "props", "foo", handler = { fail() })

    val error = assertFailsWith<IllegalArgumentException> {
      manager.render(workflow, "props", "foo", handler = { fail() })
    }
    assertEquals(
      "Expected keys to be unique for ${workflow.identifier}: key=\"foo\"",
      error.message
    )
  }

  @Test fun render_returns_child_rendering() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()

    val (composeProps, composeState) = manager.render(
      workflow,
      "props",
      key = "",
      handler = { fail() }
    )
    assertEquals("props", composeProps)
    assertEquals("initialState:props", composeState)
  }

  @Test fun onNextAction_for_children_handles_child_output() = runTest {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    val handler: StringHandler = { output ->
      action { setOutput("case output:$output") }
    }

    // Initialize the child so applyNextAction has something to work with, and so that we can send
    // an event to trigger an output.
    val (_, _, eventHandler) = manager.render(workflow, "props", key = "", handler = handler)
    manager.commitRenderedChildren()

    val appliedActionResult = async { manager.applyNextAction() }
    assertFalse(appliedActionResult.isCompleted)

    eventHandler("event!")
    val update = appliedActionResult.await().output!!.value!!

    val (_, result) = update.applyTo("props", "state")
    assertEquals("case output:workflow output:event!", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun onNextAction_for_children_handles_no_child_output() = runTest {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    val handler: StringHandler = { _ ->
      WorkflowAction.noAction()
    }

    // Initialize the child so applyNextAction has something to work with, and so that we can send
    // an event to trigger an output.
    val (_, _, eventHandler) = manager.render(workflow, "props", key = "", handler = handler)
    manager.commitRenderedChildren()

    val appliedActionResult = async { manager.applyNextAction() }
    assertFalse(appliedActionResult.isCompleted)

    eventHandler("event!")
    val update = appliedActionResult.await().output!!.value!!

    val (_, result) = update.applyTo("props", "state")
    assertEquals(null, result.output)
    assertFalse(result.stateChanged)
  }

  @Test fun render_updates_childs_output_handler() = runTest {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    fun render(handler: StringHandler) =
      manager.render(workflow, "props", key = "", handler = handler)
        .also { manager.commitRenderedChildren() }

    // First render + apply action pass – uninteresting.
    render { action { setOutput("initial handler: $it") } }
      .let { rendering ->
        rendering.eventHandler("initial output")
        val initialAction = manager.applyNextAction().output!!.value
        val (_, initialResult) = initialAction!!.applyTo("", "")
        assertEquals(
          expected = "initial handler: workflow output:initial output",
          actual = initialResult.output!!.value
        )
        assertFalse(initialResult.stateChanged)
      }

    // Do a second render + apply action, but with a different handler function.
    render {
      action {
        state = "New State"
        setOutput("second handler: $it")
      }
    }
      .let { rendering ->
        rendering.eventHandler("second output")
        val secondAction = manager.applyNextAction().output!!.value
        val (secondState, secondResult) = secondAction!!.applyTo("", "")
        assertEquals(
          expected = "second handler: workflow output:second output",
          actual = secondResult.output!!.value
        )
        assertTrue(secondResult.stateChanged)
        assertEquals("New State", secondState)
      }
  }

  // See https://github.com/square/workflow/issues/404
  @Test fun createChildSnapshot_snapshots_eagerly() {
    val manager = subtreeManagerForTest<Unit, Unit, Nothing>()
    val workflow = SnapshotTestWorkflow()
    assertEquals(0, workflow.snapshots)

    manager.render(workflow, props = Unit, key = "1", handler = { fail() })
    manager.commitRenderedChildren()
    manager.createChildSnapshots()

    assertEquals(1, workflow.snapshots)
  }

  // See https://github.com/square/workflow/issues/404
  @Test fun createChildSnapshot_serializes_lazily() {
    val manager = subtreeManagerForTest<Unit, Unit, Nothing>()
    val workflow = SnapshotTestWorkflow()
    assertEquals(0, workflow.serializes)

    manager.render(workflow, props = Unit, key = "1", handler = { fail() })
    manager.commitRenderedChildren()
    val snapshots = manager.createChildSnapshots()

    assertEquals(0, workflow.serializes)

    // Force the snapshots to serialize.
    snapshots.forEach { (_, snapshot) -> snapshot.workflowSnapshot }
    assertEquals(1, workflow.serializes)
  }

  @Test fun snapshots_applied_on_first_render_only() {
    val manager1 = subtreeManagerForTest<Unit, Unit, Nothing>()
    val workflowAble = SnapshotTestWorkflow()
    val workflowBaker = SnapshotTestWorkflow()

    manager1.render(workflowAble, props = Unit, key = "able", handler = { fail() })
    manager1.render(workflowBaker, props = Unit, key = "baker", handler = { fail() })
    manager1.commitRenderedChildren()
    val snapshots = manager1.createChildSnapshots()

    val manager2 = subtreeManagerForTest<Unit, Unit, Nothing>(snapshots)
    assertEquals(0, workflowAble.restores)
    assertEquals(0, workflowBaker.restores)

    manager2.render(workflowAble, props = Unit, key = "able", handler = { fail() })
    manager2.commitRenderedChildren()
    assertEquals(1, workflowAble.restores)

    manager2.render(workflowAble, props = Unit, key = "able", handler = { fail() })
    manager2.render(workflowBaker, props = Unit, key = "baker", handler = { fail() })
    manager2.commitRenderedChildren()
    assertEquals(1, workflowAble.restores)
    assertEquals(0, workflowBaker.restores)
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <P, S, O : Any> SubtreeManager<P, S, O>.applyNextAction() =
    select<ActionProcessingResult?> {
      onNextChildAction(this)
    } as ActionApplied<WorkflowAction<P, S, O>?>

  private fun <P, S, O : Any> subtreeManagerForTest(
    snapshotCache: Map<WorkflowNodeId, TreeSnapshot>? = null
  ) = SubtreeManager<P, S, O>(
    snapshotCache = snapshotCache,
    contextForChildren = context,
    runtimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
    emitActionToParent = { action, childResult ->
      ActionApplied(WorkflowOutput(action), childResult.stateChanged)
    }
  )
}
