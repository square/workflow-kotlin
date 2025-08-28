@file:Suppress("DEPRECATION")

package com.squareup.workflow1

import com.squareup.workflow1.testing.WorkerSink
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class WorkflowCompositionIntegrationTest {

  @Test fun `composes parent with single child`() {
    val root = TreeWorkflow("root", TreeWorkflow("leaf"))

    // Setup initial state and change the state the workflow in the tree.
    root.launchForTestingFromStartWith("initial props") {
      awaitNextRendering()
        .let {
          assertEquals("root:initial props", it.data)
          assertEquals("leaf:initial props[0]", it["leaf"].data)
        }
    }
  }

  @Test fun `composes parent with multiple children`() {
    val root = TreeWorkflow(
      "root",
      TreeWorkflow("leaf1"),
      TreeWorkflow("leaf2")
    )

    // Setup initial state and change the state the workflow in the tree.
    root.launchForTestingFromStartWith("initial props") {
      awaitNextRendering()
        .let {
          assertEquals("root:initial props", it.data)
          assertEquals("leaf1:initial props[0]", it["leaf1"].data)
          assertEquals("leaf2:initial props[1]", it["leaf2"].data)
        }
    }
  }

  @Test fun `composes complex tree`() {
    val root = TreeWorkflow(
      "root",
      TreeWorkflow(
        "middle1",
        TreeWorkflow("leaf1"),
        TreeWorkflow("leaf2")
      ),
      TreeWorkflow(
        "middle2",
        TreeWorkflow("leaf3")
      )
    )

    root.launchForTestingFromStartWith("initial props") {
      awaitNextRendering()
        .let {
          assertEquals("root:initial props", it.data)
          assertEquals("middle1:initial props[0]", it["middle1"].data)
          assertEquals("middle2:initial props[1]", it["middle2"].data)
          assertEquals("leaf1:initial props[0][0]", it["middle1", "leaf1"].data)
          assertEquals("leaf2:initial props[0][1]", it["middle1", "leaf2"].data)
          assertEquals("leaf3:initial props[1][0]", it["middle2", "leaf3"].data)
        }
    }
  }

  @Test fun `render fails when duplicate child key`() {
    val root = TreeWorkflow(
      "root",
      TreeWorkflow("leaf"),
      TreeWorkflow("leaf")
    )

    // Setup initial state and change the state the workflow in the tree.
    assertFails {
      root.launchForTestingFromStartWith("initial props") {
        awaitNextRendering()
      }
    }.let { error ->
      val causeChain = generateSequence(error) { it.cause }
      assertTrue(causeChain.any { it is IllegalArgumentException })
    }
  }

  // See https://github.com/square/workflow/issues/261.
  @Test fun `renderChild closes over latest state`() {
    val triggerChildOutput = WorkerSink<Unit>("")
    val child = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(triggerChildOutput) { action("") { setOutput(Unit) } }
    }
    val incrementState = action<Unit, Int, Int>("") {
      state += 1
    }
    val workflow = Workflow.stateful(
      initialState = 0,
      render = { _ ->
        renderChild(child) { action("") { setOutput(state) } }
        return@stateful { actionSink.send(incrementState) }
      }
    )

    workflow.launchForTestingFromStartWith {
      triggerChildOutput.send(Unit)
      assertEquals(0, awaitNextOutput())

      awaitNextRendering()
        .invoke()
      triggerChildOutput.send(Unit)

      assertEquals(1, awaitNextOutput())

      awaitNextRendering()
        .invoke()
      triggerChildOutput.send(Unit)

      assertEquals(2, awaitNextOutput())
    }
  }
}
