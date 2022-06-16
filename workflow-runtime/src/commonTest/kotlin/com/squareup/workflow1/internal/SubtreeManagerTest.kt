@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import app.cash.molecule.launchMolecule
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.WorkflowRuntimeClock
import com.squareup.workflow1.action
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.identifier
import com.squareup.workflow1.internal.SubtreeManagerTest.TestWorkflow.Rendering
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.fail

private typealias StringHandler = (String) -> WorkflowAction<String, String, String>

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
      snapshot: Snapshot?
    ): String {
      started++
      return "initialState:$props"
    }

    @Composable
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
      snapshot: Snapshot?
    ) {
      if (snapshot != null) restores++
    }

    @Composable
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
  val scope = CoroutineScope(UnconfinedTestDispatcher()) + WorkflowRuntimeClock(flowOf(Unit))

  @Test fun `render starts new child`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()

    scope.launchMolecule {
      manager.render(workflow, "props", key = "", handler = { fail() })
      assertEquals(1, workflow.started)
    }
  }

  @Test fun `render doesn't start existing child`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    @Composable fun render() = manager.render(workflow, "props", key = "", handler = { fail() })
      .also { manager.commitRenderedChildren() }

    scope.launchMolecule {
      render()
      render()

      assertEquals(1, workflow.started)
    }
  }

  @Test fun `render restarts child after tearing down`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    @Composable fun render() {
      manager.render(workflow, "props", key = "", handler = { fail() })
      manager.commitRenderedChildren()
    }
    scope.launchMolecule {
      render()
      assertEquals(1, workflow.started)

      // Render without rendering child.
      manager.commitRenderedChildren()
      assertEquals(1, workflow.started)

      render()
      assertEquals(2, workflow.started)
    }
  }

  @Test fun `render throws on duplicate key`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    scope.launchMolecule {
      manager.render(workflow, "props", "foo", handler = { fail() })

      val error = assertFailsWith<IllegalArgumentException> {
        manager.render(workflow, "props", "foo", handler = { fail() })
      }
      assertEquals(
        "Expected keys to be unique for ${workflow.identifier}: key=\"foo\"",
        error.message
      )
    }
  }

  @Test fun `render returns child rendering`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()

    scope.launchMolecule {
      val (composeProps, composeState) = manager.render(
        workflow, "props", key = "", handler = { fail() }
      )
      assertEquals("props", composeProps)
      assertEquals("initialState:props", composeState)
    }
  }

  @Test fun `tick children handles child output`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    val handler: StringHandler = { output ->
      action { setOutput("case output:$output") }
    }

    scope.launchMolecule {
      // Initialize the child so tickChildren has something to work with, and so that we can send
      // an event to trigger an output.
      val (_, _, eventHandler) = manager.render(workflow, "props", key = "", handler = handler)
      manager.commitRenderedChildren()

      runBlocking {
        val tickOutput = async { manager.tickAction() }
        assertFalse(tickOutput.isCompleted)

        eventHandler("event!")
        val update = tickOutput.await().value!!

        val (_, output) = update.applyTo("props", "state")
        assertEquals("case output:workflow output:event!", output?.value)
      }
    }
  }

  @Test fun `render updates child's output handler`() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    @Composable fun render(handler: StringHandler) =
      manager.render(workflow, "props", key = "", handler = handler)
        .also { manager.commitRenderedChildren() }

    scope.launchMolecule {
      // First render + tick pass – uninteresting.
      render { action { setOutput("initial handler: $it") } }
        .let { rendering ->
          runBlocking {
            rendering.eventHandler("initial output")
            val initialAction = manager.tickAction().value
            val (_, initialOutput) = initialAction!!.applyTo("", "")
            assertEquals("initial handler: workflow output:initial output", initialOutput?.value)
          }
        }

      // Do a second render + tick, but with a different handler function.
      render { action { setOutput("second handler: $it") } }
        .let { rendering ->
          runBlocking {
            rendering.eventHandler("second output")
            val secondAction = manager.tickAction().value
            val (_, secondOutput) = secondAction!!.applyTo("", "")
            assertEquals("second handler: workflow output:second output", secondOutput?.value)
          }
        }
    }
  }

  // See https://github.com/square/workflow/issues/404
  @Test fun `createChildSnapshot snapshots eagerly`() {
    val manager = subtreeManagerForTest<Unit, Unit, Nothing>()
    val workflow = SnapshotTestWorkflow()
    assertEquals(0, workflow.snapshots)

    scope.launchMolecule {
      manager.render(workflow, props = Unit, key = "1", handler = { fail() })
      manager.commitRenderedChildren()
      manager.createChildSnapshots()

      assertEquals(1, workflow.snapshots)
    }
  }

  // See https://github.com/square/workflow/issues/404
  @Test fun `createChildSnapshot serializes lazily`() {
    val manager = subtreeManagerForTest<Unit, Unit, Nothing>()
    val workflow = SnapshotTestWorkflow()
    assertEquals(0, workflow.serializes)

    scope.launchMolecule {
      manager.render(workflow, props = Unit, key = "1", handler = { fail() })
      manager.commitRenderedChildren()
      val snapshots = manager.createChildSnapshots()

      assertEquals(0, workflow.serializes)

      // Force the snapshots to serialize.
      snapshots.forEach { (_, snapshot) -> snapshot.workflowSnapshot }
      assertEquals(1, workflow.serializes)
    }
  }

  @Test fun `snapshots applied on first render only`() {
    val manager1 = subtreeManagerForTest<Unit, Unit, Nothing>()
    val workflowAble = SnapshotTestWorkflow()
    val workflowBaker = SnapshotTestWorkflow()

    scope.launchMolecule {
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
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <P, S, O : Any> SubtreeManager<P, S, O>.tickAction() =
    select<ActionProcessingResult?> {
      tickChildren(this)
    } as WorkflowOutput<WorkflowAction<P, S, O>?>

  private fun <P, S, O : Any> subtreeManagerForTest(
    snapshotCache: Map<WorkflowNodeId, TreeSnapshot>? = null
  ) = SubtreeManager<P, S, O>(snapshotCache, context, emitActionToParent = { WorkflowOutput(it) })
}
