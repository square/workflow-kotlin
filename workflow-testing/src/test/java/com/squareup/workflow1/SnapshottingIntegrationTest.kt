@file:Suppress("DEPRECATION")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.testing.WorkflowTestParams
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromCompleteSnapshot
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SnapshottingIntegrationTest {

  @Test fun `snapshots and restores single workflow`() {
    val root = TreeWorkflow("root")
    var snapshot: TreeSnapshot? = null

    // Setup initial state and change the state the workflow in the tree.
    root.launchForTestingFromStartWith("initial props") {
      awaitNextRendering()
        .let {
          assertEquals("root:initial props", it.data)
          it.setData("new data")
        }

      assertEquals("root:new data", awaitNextRendering().data)

      snapshot = awaitNextSnapshot()
    }

    root.launchForTestingFromStartWith(
      props = "unused props",
      testParams = WorkflowTestParams(startFrom = StartFromCompleteSnapshot(snapshot!!))
    ) {
      assertEquals("root:new data", awaitNextRendering().data)
    }
  }

  @Test fun `snapshots and restores parent child workflows`() {
    val root = TreeWorkflow("root", TreeWorkflow("leaf"))
    var snapshot: TreeSnapshot? = null

    // Setup initial state and change the state the workflow in the tree.
    root.launchForTestingFromStartWith("initial props") {
      awaitNextRendering()
        .let {
          assertEquals("root:initial props", it.data)
          it["leaf"].setData("new leaf data")
        }
      awaitNextRendering()
        .setData("new root data")

      awaitNextRendering()
        .let {
          assertEquals("root:new root data", it.data)
          assertEquals("leaf:new leaf data", it["leaf"].data)
        }

      snapshot = awaitNextSnapshot()
    }

    root.launchForTestingFromStartWith(
      props = "unused props",
      testParams = WorkflowTestParams(startFrom = StartFromCompleteSnapshot(snapshot!!))
    ) {
      awaitNextRendering()
        .let {
          assertEquals("root:new root data", it.data)
          assertEquals("leaf:new leaf data", it["leaf"].data)
        }
    }
  }

  @Test fun `snapshots and restores complex tree`() {
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
    var snapshot: TreeSnapshot? = null

    // Setup initial state and change the state of two workflows in the tree.
    root.launchForTestingFromStartWith("initial props") {
      awaitNextRendering()
        .let {
          assertEquals("root:initial props", it.data)
          assertEquals("middle1:initial props[0]", it["middle1"].data)
          assertEquals("middle2:initial props[1]", it["middle2"].data)
          assertEquals("leaf1:initial props[0][0]", it["middle1", "leaf1"].data)
          assertEquals("leaf2:initial props[0][1]", it["middle1", "leaf2"].data)
          assertEquals("leaf3:initial props[1][0]", it["middle2", "leaf3"].data)

          it["middle1", "leaf2"].setData("new leaf data")
        }
      awaitNextRendering()
        .setData("new root data")

      awaitNextRendering()
        .let {
          assertEquals("root:new root data", it.data)
          assertEquals("middle1:initial props[0]", it["middle1"].data)
          assertEquals("middle2:initial props[1]", it["middle2"].data)
          assertEquals("leaf1:initial props[0][0]", it["middle1", "leaf1"].data)
          assertEquals("leaf2:new leaf data", it["middle1", "leaf2"].data)
          assertEquals("leaf3:initial props[1][0]", it["middle2", "leaf3"].data)
        }

      snapshot = awaitNextSnapshot()
    }

    root.launchForTestingFromStartWith(
      props = "unused props",
      testParams = WorkflowTestParams(startFrom = StartFromCompleteSnapshot(snapshot!!))
    ) {
      awaitNextRendering()
        .let {
          assertEquals("root:new root data", it.data)
          assertEquals("middle1:initial props[0]", it["middle1"].data)
          assertEquals("middle2:initial props[1]", it["middle2"].data)
          assertEquals("leaf1:initial props[0][0]", it["middle1", "leaf1"].data)
          assertEquals("leaf2:new leaf data", it["middle1", "leaf2"].data)
          assertEquals("leaf3:initial props[1][0]", it["middle2", "leaf3"].data)
        }
    }
  }

  // See https://github.com/square/workflow/issues/404
  @OptIn(WorkflowExperimentalRuntime::class)
  @Test
  fun `descendant snapshots are independent over state transitions`() {
    if (JvmTestRuntimeConfigTools.getTestRuntimeConfig()
        .contains(RuntimeConfigOptions.PARTIAL_TREE_RENDERING) ||
      JvmTestRuntimeConfigTools.getTestRuntimeConfig()
        .contains(RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES)
    ) {
      // Partial Tree Rendering and Render Only When State changes means this test hangs because
      // there is no render/snapshot as state does not change!
      return
    }
    val workflow = Workflow.stateful<String, String, Nothing, () -> Unit>(
      initialState = { props, _ -> props },
      onPropsChanged = { _, new, _ -> new },
      // Return an event handler that can be used to trigger new renderings.
      render = { _, _ -> { actionSink.send(noAction()) } },
      snapshot = { state ->
        Snapshot.write {
          it.writeUtf8WithLength(state)
        }
      }
    )
    // This test specifically needs to test snapshots from a non-flat workflow tree.
    val root = Workflow.stateless<String, Nothing, () -> Unit> {
      renderChild(workflow, it)
    }

    root.launchForTestingFromStartWith("props1") {
      val snapshot1 = awaitNextSnapshot()

      // Change the props (and thus the state) to make a different snapshot.
      sendProps("props2")
      val snapshot2 = awaitNextSnapshot()

      // Trigger a new render pass, but with the same snapshot.
      awaitNextRendering().invoke()
      val snapshot3 = awaitNextSnapshot()

      assertNotEquals(snapshot1, snapshot2)
      assertEquals(snapshot2, snapshot3)
    }
  }
}
