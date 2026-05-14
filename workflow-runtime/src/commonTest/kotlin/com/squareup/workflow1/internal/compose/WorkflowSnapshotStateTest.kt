package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

internal class WorkflowSnapshotStateTest {

  @BeforeTest fun setUp() {
    // WorkflowSnapshotState is a snapshot StateObject; writes go through the global write
    // observer registered by GlobalSnapshotManager. Without this flag, the observer tries to
    // launch on Dispatchers.Main, which isn't installed in plain JVM tests.
    enableImmediateApplyForTests()
  }

  /**
   * Runs [block] inside a mutable snapshot and reports whether any write was observed against
   * [target]. The snapshot is applied at the end so its effects become visible outside.
   */
  private fun observeWritesTo(target: WorkflowSnapshotState, block: () -> Unit): Boolean {
    var observed = false
    val snapshot = Snapshot.takeMutableSnapshot(writeObserver = { written ->
      if (written === target) observed = true
    })
    try {
      snapshot.enter(block)
      snapshot.apply().check()
    } finally {
      snapshot.dispose()
    }
    return observed
  }

  // Construction / accessors -----------------------------------------------------------------

  @Test fun constructor_stores_initial_values_and_peekState_returns_initial_state() {
    val onOutput: (Any?) -> Unit = {}
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s")
    assertEquals("s", state.peekState())
  }

  @Test fun firstStateRecord_matches_initial_values() {
    val onOutput: (Any?) -> Unit = {}
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s")
    val record = state.firstStateRecord as WorkflowSnapshotState.Record
    assertEquals("p", record.props)
    assertSame(onOutput, record.onOutput)
    assertEquals("s", record.state)
  }

  @Test fun record_create_returns_fresh_copy_with_current_values() {
    val onOutput: (Any?) -> Unit = {}
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s")
    val original = state.firstStateRecord as WorkflowSnapshotState.Record
    val copy = original.create() as WorkflowSnapshotState.Record

    assertNotSame(original, copy)
    assertEquals(original.props, copy.props)
    assertSame(original.onOutput, copy.onOutput)
    assertEquals(original.state, copy.state)
  }

  @Test fun record_assign_copies_fields_from_source() {
    val src = WorkflowSnapshotState.Record(props = "p2", onOutput = { /* noop */ }, state = "s2")
    val dst = WorkflowSnapshotState.Record(props = "p1", onOutput = null, state = "s1")
    dst.assign(src)
    assertEquals(src.props, dst.props)
    assertSame(src.onOutput, dst.onOutput)
    assertEquals(src.state, dst.state)
  }

  @Test fun prependStateRecord_replaces_head_record() {
    val state = WorkflowSnapshotState(props = "p", onOutput = null, state = "s")
    val replacement = WorkflowSnapshotState.Record(props = "p2", onOutput = null, state = "s2")
    state.prependStateRecord(replacement)
    assertSame(replacement, state.firstStateRecord)
  }

  // updateAndGetState ------------------------------------------------------------------------

  @Test fun updateAndGetState_no_change_does_not_write() {
    val onOutput: (Any?) -> Unit = {}
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s")
    var propsChangedCalls = 0
    val wrote = observeWritesTo(state) {
      val returned = state.updateAndGetState(
        newProps = "p",
        newOnOutput = onOutput,
        didPropsChange = false,
        didOnOutputChange = false,
      ) { _, _ ->
        propsChangedCalls++
        fail("onPropsChanged should not be invoked")
      }
      assertEquals("s", returned)
    }
    assertEquals(0, propsChangedCalls)
    assertEquals(false, wrote)
    assertEquals("s", state.peekState())
  }

  @Test fun updateAndGetState_invokes_onPropsChanged_when_didPropsChange_true() {
    val state = WorkflowSnapshotState(props = "p1", onOutput = null, state = "s1")
    var seen: Pair<Any?, Any?>? = null
    val returned = state.updateAndGetState(
      newProps = "p2",
      newOnOutput = null,
      didPropsChange = true,
      didOnOutputChange = false,
    ) { oldProps, oldState ->
      seen = oldProps to oldState
      "s-from-handler"
    }
    assertEquals("p1" to "s1", seen)
    assertEquals("s-from-handler", returned)
    assertEquals("s-from-handler", state.peekState())
  }

  @Test fun updateAndGetState_honors_didPropsChange_true_even_when_props_equal() {
    val state = WorkflowSnapshotState(props = "p", onOutput = null, state = "s1")
    var calls = 0
    val returned = state.updateAndGetState(
      newProps = "p",
      newOnOutput = null,
      didPropsChange = true,
      didOnOutputChange = false,
    ) { _, _ ->
      calls++
      "s2"
    }
    assertEquals(1, calls)
    assertEquals("s2", returned)
    assertEquals("s2", state.peekState())
  }

  @Test fun updateAndGetState_honors_didPropsChange_false_even_when_props_differ() {
    val state = WorkflowSnapshotState(props = "p1", onOutput = null, state = "s")
    val returned = state.updateAndGetState(
      newProps = "p2",
      newOnOutput = null,
      didPropsChange = false,
      didOnOutputChange = false,
    ) { _, _ ->
      fail("onPropsChanged should not be invoked when didPropsChange is false")
    }
    assertEquals("s", returned)
    // The caller asserted "nothing changed", so no write occurs at all — the new props value
    // is silently ignored. This documents the contract: didPropsChange=false trusts the caller
    // even when it's a lie.
    val record = state.firstStateRecord as WorkflowSnapshotState.Record
    assertEquals("p1", record.props)
  }

  @Test fun updateAndGetState_null_flag_skips_onPropsChanged_when_equal_by_equals() {
    val state = WorkflowSnapshotState(props = "p", onOutput = null, state = "s")
    var calls = 0
    val returned = state.updateAndGetState(
      newProps = "p",
      newOnOutput = null,
      didPropsChange = null,
      didOnOutputChange = null,
    ) { _, _ ->
      calls++
      "should-not-be-used"
    }
    assertEquals(0, calls)
    assertEquals("s", returned)
  }

  @Test fun updateAndGetState_null_flag_invokes_onPropsChanged_when_not_equal() {
    val state = WorkflowSnapshotState(props = "p1", onOutput = null, state = "s1")
    var calls = 0
    val returned = state.updateAndGetState(
      newProps = "p2",
      newOnOutput = null,
      didPropsChange = null,
      didOnOutputChange = null,
    ) { _, _ ->
      calls++
      "s2"
    }
    assertEquals(1, calls)
    assertEquals("s2", returned)
    assertEquals("s2", state.peekState())
  }

  @Test fun updateAndGetState_onPropsChanged_returning_oldState_still_updates_props() {
    val state = WorkflowSnapshotState(props = "p1", onOutput = null, state = "s")
    val returned = state.updateAndGetState(
      newProps = "p2",
      newOnOutput = null,
      didPropsChange = true,
      didOnOutputChange = false,
    ) { _, oldState ->
      // Return the existing state, signalling state didn't change.
      oldState
    }
    assertEquals("s", returned)
    val record = state.firstStateRecord as WorkflowSnapshotState.Record
    assertEquals("p2", record.props)
    assertEquals("s", record.state)
  }

  @Test fun updateAndGetState_onOutput_only_change_writes_new_onOutput() {
    val initialOnOutput: (Any?) -> Unit = {}
    val newOnOutput: (Any?) -> Unit = {}
    val state = WorkflowSnapshotState(props = "p", onOutput = initialOnOutput, state = "s")
    val wrote = observeWritesTo(state) {
      val returned = state.updateAndGetState(
        newProps = "p",
        newOnOutput = newOnOutput,
        didPropsChange = false,
        didOnOutputChange = true,
      ) { _, _ ->
        fail("onPropsChanged should not be invoked when didPropsChange is false")
      }
      assertEquals("s", returned)
    }
    assertEquals(true, wrote)
    val record = state.firstStateRecord as WorkflowSnapshotState.Record
    assertSame(newOnOutput, record.onOutput)
  }

  @Test fun updateAndGetState_all_unchanged_skips_write() {
    val onOutput: (Any?) -> Unit = {}
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s")
    val wrote = observeWritesTo(state) {
      state.updateAndGetState(
        newProps = "p",
        newOnOutput = onOutput,
        didPropsChange = false,
        didOnOutputChange = false,
      ) { _, _ -> fail("onPropsChanged should not be invoked") }
    }
    assertEquals(false, wrote)
  }

  @Test fun updateAndGetState_writes_participate_in_snapshot_isolation() {
    val state = WorkflowSnapshotState(props = "p1", onOutput = null, state = "s1")
    val snapshot = Snapshot.takeMutableSnapshot()
    try {
      snapshot.enter {
        state.updateAndGetState(
          newProps = "p2",
          newOnOutput = null,
          didPropsChange = true,
          didOnOutputChange = false,
        ) { _, _ -> "s2" }
        // Inside the snapshot, the new value is visible.
        assertEquals("s2", state.peekState())
      }
      // Outside the snapshot, the write is still invisible until apply().
      assertEquals("s1", state.peekState())
      snapshot.apply().check()
      assertEquals("s2", state.peekState())
    } finally {
      snapshot.dispose()
    }
  }

  // applyAction ------------------------------------------------------------------------------

  @Test fun applyAction_no_state_change_does_not_invoke_onNewState_or_write() {
    val state = WorkflowSnapshotState(props = "p", onOutput = null, state = "s")
    var newStateCalls = 0
    val noOp: WorkflowAction<Any?, Any?, Any?> = action("noop") { /* leave state alone */ }
    val wrote = observeWritesTo(state) {
      state.applyAction(noOp) { newStateCalls++ }
    }
    assertEquals(0, newStateCalls)
    assertEquals(false, wrote)
    assertEquals("s", state.peekState())
  }

  @Test fun applyAction_state_change_invokes_onNewState_and_updates_peekState() {
    val state = WorkflowSnapshotState(props = "p", onOutput = null, state = "s1")
    var newStateCalls = 0
    val setState: WorkflowAction<Any?, Any?, Any?> = action("set") { this.state = "s2" }
    state.applyAction(setState) { newStateCalls++ }
    assertEquals(1, newStateCalls)
    assertEquals("s2", state.peekState())
  }

  @Test fun applyAction_with_output_invokes_stored_onOutput() {
    val outputs = mutableListOf<Any?>()
    val onOutput: (Any?) -> Unit = { outputs += it }
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s")
    val emit: WorkflowAction<Any?, Any?, Any?> = action("emit") { setOutput("the-output") }
    state.applyAction(emit) { fail("state did not change; onNewState should not be invoked") }
    assertEquals(listOf<Any?>("the-output"), outputs)
  }

  @Test fun applyAction_with_output_and_null_onOutput_does_not_crash() {
    val state = WorkflowSnapshotState(props = "p", onOutput = null, state = "s")
    val emit: WorkflowAction<Any?, Any?, Any?> = action("emit") { setOutput("dropped") }
    state.applyAction(emit) { fail("state did not change; onNewState should not be invoked") }
    // No assertion needed beyond no-throw; state remains unchanged.
    assertEquals("s", state.peekState())
  }

  @Test fun applyAction_with_state_and_output_invokes_both_callbacks_in_order() {
    val callOrder = mutableListOf<String>()
    val onOutput: (Any?) -> Unit = { callOrder += "output=$it" }
    val state = WorkflowSnapshotState(props = "p", onOutput = onOutput, state = "s1")
    val both: WorkflowAction<Any?, Any?, Any?> = action("both") {
      this.state = "s2"
      setOutput("out")
    }
    state.applyAction(both) { callOrder += "state=${state.peekState()}" }
    // The action helper writes state first, then propagates output.
    assertEquals(listOf("state=s2", "output=out"), callOrder)
    assertEquals("s2", state.peekState())
  }

  @Test fun applyAction_uses_latest_props_after_updateAndGetState() {
    val state = WorkflowSnapshotState(props = "p1", onOutput = null, state = "s")
    state.updateAndGetState(
      newProps = "p2",
      newOnOutput = null,
      didPropsChange = true,
      didOnOutputChange = false,
    ) { _, oldState -> oldState }

    var seenProps: Any? = null
    val capture: WorkflowAction<Any?, Any?, Any?> = action("capture") {
      seenProps = props
    }
    state.applyAction(capture) { fail("state did not change; onNewState should not be invoked") }
    assertEquals("p2", seenProps)
    // Sanity check: applyAction observed the latest props rather than the initial one.
    assertTrue(seenProps != "p1")
  }
}
