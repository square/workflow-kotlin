@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.saveable.SaverScope
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeUtf8WithLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class WorkflowSnapshotSaverTest {

  /** A test stub that records calls to its lifecycle methods. */
  private class RecordingWorkflow : StatefulWorkflow<String, String, Nothing, Unit>() {
    val initialStateCalls = mutableListOf<Pair<String, Snapshot?>>()
    val initialStateScopes = mutableListOf<CoroutineScope>()
    val snapshotStateCalls = mutableListOf<String>()

    override fun initialState(props: String, snapshot: Snapshot?): String {
      initialStateCalls += props to snapshot
      // Read the bytes back to mimic real workflows that decode their state.
      val restored = snapshot?.bytes?.parse { it.readUtf8WithLength() }
      return restored ?: "init($props)"
    }

    override fun initialState(
      props: String,
      snapshot: Snapshot?,
      workflowScope: CoroutineScope
    ): String {
      initialStateScopes += workflowScope
      return super.initialState(props, snapshot, workflowScope)
    }

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, Nothing>
    ) = Unit

    override fun snapshotState(state: String): Snapshot? {
      snapshotStateCalls += state
      return Snapshot.write { it.writeUtf8WithLength(state) }
    }
  }

  private val saverScope = SaverScope { true }

  @Test fun save_calls_workflow_snapshotState_with_value() {
    val workflow = RecordingWorkflow()
    val saver = WorkflowSnapshotSaver(
      initialProps = "props",
      statefulWorkflow = workflow,
      workflowTracer = null,
      workflowScope = TestScope(),
    )
    val saved = with(saverScope) { with(saver) { save("the-state") } }
    assertEquals(listOf("the-state"), workflow.snapshotStateCalls)
    val decoded = saved!!.bytes.parse { it.readUtf8WithLength() }
    assertEquals("the-state", decoded)
  }

  @Test fun save_returns_null_when_workflow_returns_null() {
    val workflow = object : StatefulWorkflow<Unit, String, Nothing, Unit>() {
      override fun initialState(props: Unit, snapshot: Snapshot?) = ""
      override fun render(
        renderProps: Unit,
        renderState: String,
        context: RenderContext<Unit, String, Nothing>
      ) = Unit

      override fun snapshotState(state: String): Snapshot? = null
    }
    val saver = WorkflowSnapshotSaver(
      initialProps = Unit,
      statefulWorkflow = workflow,
      workflowTracer = null,
      workflowScope = TestScope(),
    )
    val saved = with(saverScope) { with(saver) { save("") } }
    assertNull(saved)
  }

  @Test fun restore_invokes_initialState_with_props_and_snapshot_and_scope() {
    val workflow = RecordingWorkflow()
    val scope = TestScope()
    val saver = WorkflowSnapshotSaver(
      initialProps = "the-props",
      statefulWorkflow = workflow,
      workflowTracer = null,
      workflowScope = scope,
    )
    val snapshot = Snapshot.write { it.writeUtf8WithLength("restored-state") }
    val restored = saver.restore(snapshot)

    assertEquals("restored-state", restored)
    val expectedCalls: List<Pair<String, Snapshot?>> = listOf("the-props" to snapshot)
    assertEquals(expectedCalls, workflow.initialStateCalls)
    // The 3-arg overload that takes workflowScope is the one called; verify our scope was passed.
    assertEquals(1, workflow.initialStateScopes.size)
    assertSame(scope, workflow.initialStateScopes.single())
  }

  @Test fun save_then_restore_round_trips_state() {
    val workflow = RecordingWorkflow()
    val saver = WorkflowSnapshotSaver(
      initialProps = "p",
      statefulWorkflow = workflow,
      workflowTracer = null,
      workflowScope = TestScope(),
    )
    val saved = with(saverScope) { with(saver) { save("hello") } }
    val restored = saver.restore(saved!!)
    assertEquals("hello", restored)
  }
}
