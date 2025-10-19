package com.squareup.workflow1.testing

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.stateful
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Tests for WorkflowTurbine to verify that awaitNextRendering, awaitNextOutput, and
 * awaitNextSnapshot all work correctly with the shareIn-based implementation.
 */
class WorkflowTurbineTest {

  @Test fun `awaitNextRendering returns first rendering`() {
    val workflow = Workflow.stateful<Int, Nothing, Int>(
      initialState = 42,
      render = { state: Int ->
        state
      }
    )

    workflow.renderForTest {
      // First rendering should be 42
      assertEquals(42, awaitNextRendering())
    }
  }

  @Test fun `awaitNextSnapshot returns first snapshot`() {
    val workflow = Workflow.stateful<Int, Nothing, Int>(
      initialState = { snapshot: Snapshot? -> 42 },
      render = { state: Int ->
        state
      },
      snapshot = { state: Int -> Snapshot.of(state) }
    )

    workflow.renderForTest {
      // First snapshot should exist
      val firstSnapshot = awaitNextSnapshot()
      assertNotNull(firstSnapshot)
    }
  }

  @Test fun `firstRendering property is accessible`() {
    val workflow = Workflow.stateful<String, Nothing, String>(
      initialState = "hello",
      render = { state: String ->
        state
      }
    )

    workflow.renderForTest {
      // First rendering property should be accessible
      assertEquals("hello", firstRendering)
      // awaitNextRendering should return the same value
      assertEquals("hello", awaitNextRendering())
    }
  }

  @Test fun `firstSnapshot property is accessible`() {
    val workflow = Workflow.stateful<String, Nothing, String>(
      initialState = { snapshot: Snapshot? -> "hello" },
      render = { state: String ->
        state
      },
      snapshot = { state: String -> Snapshot.of(state) }
    )

    workflow.renderForTest {
      // First snapshot property should be accessible
      assertNotNull(firstSnapshot)
      // awaitNextSnapshot should return the same value
      assertEquals(firstSnapshot, awaitNextSnapshot())
    }
  }

  // Workflow that can increment state
  private object IncrementWorkflow : StatefulWorkflow<Unit, Int, Nothing, Pair<Int, () -> Unit>>() {
    override fun initialState(props: Unit, snapshot: Snapshot?) = 0

    override fun snapshotState(state: Int): Snapshot = Snapshot.of(state)

    override fun render(
      renderProps: Unit,
      renderState: Int,
      context: RenderContext<Unit, Int, Nothing>
    ): Pair<Int, () -> Unit> {
      val increment = {
        context.actionSink.send(
          action("increment") {
            state = renderState + 1
          }
        )
      }
      return renderState to increment
    }
  }

  @Test fun `awaitNextRendering and awaitNextSnapshot are independent`() {
    IncrementWorkflow.renderForTest {
      // Get first rendering
      val (value0, increment0) = awaitNextRendering()
      assertEquals(0, value0)

      // Trigger state change BEFORE consuming snapshot
      increment0()

      // Now get first snapshot - should still be for state 0
      val snapshot0 = awaitNextSnapshot()
      assertNotNull(snapshot0)

      // Now get second rendering - should be state 1
      val (value1, increment1) = awaitNextRendering()
      assertEquals(1, value1)

      // And second snapshot - should be for state 1
      val snapshot1 = awaitNextSnapshot()
      assertNotNull(snapshot1)
      assertNotEquals(snapshot0, snapshot1)

      // Trigger another change
      increment1()

      // Third rendering
      val (value2, _) = awaitNextRendering()
      assertEquals(2, value2)

      // Third snapshot
      val snapshot2 = awaitNextSnapshot()
      assertNotNull(snapshot2)
      assertNotEquals(snapshot1, snapshot2)
    }
  }

  @Test fun `awaitNextSnapshot and awaitNextRendering are synchronized`() {
    IncrementWorkflow.renderForTest {
      // Consume first rendering
      val (value0, increment) = awaitNextRendering()
      assertEquals(0, value0)

      // Trigger state change
      increment()

      // Consume second rendering
      val (value1, _) = awaitNextRendering()
      assertEquals(1, value1)

      // Now consume snapshots - they should be in sync
      val snapshot0 = awaitNextSnapshot()
      assertNotNull(snapshot0)

      val snapshot1 = awaitNextSnapshot()
      assertNotNull(snapshot1)
      assertNotEquals(snapshot0, snapshot1)
    }
  }

  @Test fun `shareIn works - both turbines receive same emissions`() {
    IncrementWorkflow.renderForTest {
      // Consume renderings first
      val (value0, increment0) = awaitNextRendering()
      assertEquals(0, value0)

      increment0()
      val (value1, increment1) = awaitNextRendering()
      assertEquals(1, value1)

      increment1()
      val (value2, _) = awaitNextRendering()
      assertEquals(2, value2)

      // Now consume snapshots - should have all 3 available because shareIn broadcasted to both
      val snapshot0 = awaitNextSnapshot()
      assertNotNull(snapshot0)

      val snapshot1 = awaitNextSnapshot()
      assertNotNull(snapshot1)

      val snapshot2 = awaitNextSnapshot()
      assertNotNull(snapshot2)

      // All snapshots should be different
      assertNotEquals(snapshot0, snapshot1)
      assertNotEquals(snapshot1, snapshot2)
      assertNotEquals(snapshot0, snapshot2)
    }
  }

  // Workflow that emits outputs
  private object OutputWorkflow : StatefulWorkflow<Unit, Int, String, Pair<Int, () -> Unit>>() {
    override fun initialState(props: Unit, snapshot: Snapshot?) = 0

    override fun snapshotState(state: Int): Snapshot? = null

    override fun render(
      renderProps: Unit,
      renderState: Int,
      context: RenderContext<Unit, Int, String>
    ): Pair<Int, () -> Unit> {
      val emitOutput = {
        context.actionSink.send(
          action("emit") {
            state = renderState + 1
            setOutput("output-$renderState")
          }
        )
      }
      return renderState to emitOutput
    }
  }

  @Test fun `awaitNextOutput receives workflow outputs`() {
    OutputWorkflow.renderForTest {
      // Get first rendering
      val (value0, emit0) = awaitNextRendering()
      assertEquals(0, value0)

      // Trigger output
      emit0()

      // Should receive output
      val output0 = awaitNextOutput()
      assertEquals("output-0", output0)

      // Should also get next rendering
      val (value1, emit1) = awaitNextRendering()
      assertEquals(1, value1)

      // Trigger another output
      emit1()

      // Should receive second output
      val output1 = awaitNextOutput()
      assertEquals("output-1", output1)

      // And third rendering
      val (value2, _) = awaitNextRendering()
      assertEquals(2, value2)
    }
  }

  @Test fun `all three await methods work together independently`() {
    OutputWorkflow.renderForTest {
      // Get first rendering
      val (value0, emit0) = awaitNextRendering()
      assertEquals(0, value0)

      // Trigger output and state change
      emit0()

      // Can consume in any order - output first
      val output0 = awaitNextOutput()
      assertEquals("output-0", output0)

      // Then rendering
      val (value1, emit1) = awaitNextRendering()
      assertEquals(1, value1)

      // Then snapshot
      val snapshot0 = awaitNextSnapshot()
      assertNotNull(snapshot0)

      // Trigger another change
      emit1()

      // Consume in different order - snapshot first
      val snapshot1 = awaitNextSnapshot()
      assertNotNull(snapshot1)

      // Then output
      val output1 = awaitNextOutput()
      assertEquals("output-1", output1)

      // Then rendering
      val (value2, _) = awaitNextRendering()
      assertEquals(2, value2)
    }
  }

  @Test fun `multiple state changes produce multiple emissions for all flows`() {
    IncrementWorkflow.renderForTest {
      val (value0, increment0) = awaitNextRendering()
      assertEquals(0, value0)

      // Trigger 3 rapid state changes
      increment0()
      val (_, increment1) = awaitNextRendering()
      increment1()
      val (_, increment2) = awaitNextRendering()
      increment2()

      // Should have renderings 1, 2, 3 available
      val (value3, _) = awaitNextRendering()
      assertEquals(3, value3)

      // Should also have all snapshots 0, 1, 2, 3 available
      val snapshot0 = awaitNextSnapshot()
      val snapshot1 = awaitNextSnapshot()
      val snapshot2 = awaitNextSnapshot()
      val snapshot3 = awaitNextSnapshot()

      assertNotNull(snapshot0)
      assertNotNull(snapshot1)
      assertNotNull(snapshot2)
      assertNotNull(snapshot3)

      // All should be different
      assertNotEquals(snapshot0, snapshot1)
      assertNotEquals(snapshot1, snapshot2)
      assertNotEquals(snapshot2, snapshot3)
    }
  }
}
