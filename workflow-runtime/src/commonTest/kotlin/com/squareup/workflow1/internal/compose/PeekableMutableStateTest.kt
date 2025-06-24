package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

internal class PeekableMutableStateTest {

  @BeforeTest fun setUp() {
    // PeekableMutableState is a snapshot StateObject; writing to it triggers the global write
    // observer registered by GlobalSnapshotManager. Without this flag, the observer tries to
    // launch on Dispatchers.Main, which isn't installed in plain JVM tests.
    enableImmediateApplyForTests()
  }

  @Test fun returns_initial_value() {
    val state = PeekableMutableState("init")
    assertEquals("init", state.value)
  }

  @Test fun assignment_updates_value() {
    val state = PeekableMutableState("init")
    state.value = "next"
    assertEquals("next", state.value)
  }

  @Test fun component1_reads_and_component2_writes() {
    val state = PeekableMutableState(1)
    val (read, write) = state
    assertEquals(1, read)
    write(42)
    assertEquals(42, state.value)
  }

  @Test fun setWithInvalidator_invokes_invalidator_on_change() {
    val state = PeekableMutableState("init")
    var calls = 0
    state.setWithInvalidator("changed") { calls++ }
    assertEquals(1, calls)
    assertEquals("changed", state.value)
  }

  @Test fun setWithInvalidator_skips_invalidator_when_value_equals_existing() {
    val state = PeekableMutableState("same")
    var calls = 0
    state.setWithInvalidator("same") { calls++ }
    assertEquals(0, calls)
    assertEquals("same", state.value)
  }

  @Test fun setWithInvalidator_accepts_null_invalidator() {
    val state = PeekableMutableState(0)
    state.setWithInvalidator(7, invalidator = null)
    assertEquals(7, state.value)
  }

  @Test fun assignment_does_not_invoke_an_invalidator() {
    // The plain `value =` setter is documented to delegate to setWithInvalidator with null. This
    // test pins that contract: writes via the setter should not trigger any side effect beyond
    // updating the value (i.e., the no-op null invalidator path).
    val state = PeekableMutableState("init")
    state.value = "next"
    // No assertion beyond no-throw — the contract is "no invalidator to invoke".
    assertEquals("next", state.value)
  }

  @Test fun snapshot_isolation_writes_are_invisible_until_apply() {
    val state = PeekableMutableState("init")
    val snapshot = Snapshot.takeMutableSnapshot()
    try {
      snapshot.enter { state.value = "in-snapshot" }
      // Outside the snapshot, the global view still sees the original value.
      assertEquals("init", state.value)
      snapshot.apply().check()
      assertEquals("in-snapshot", state.value)
    } finally {
      snapshot.dispose()
    }
  }

  @Test fun snapshot_isolation_reads_inside_snapshot_see_writes_inside_snapshot() {
    val state = PeekableMutableState("init")
    val snapshot = Snapshot.takeMutableSnapshot()
    try {
      snapshot.enter {
        state.value = "in-snapshot"
        assertEquals("in-snapshot", state.value)
      }
    } finally {
      snapshot.dispose()
    }
  }

  @Test fun setWithInvalidator_uses_equals_not_identity() {
    // Verifies that the equality check uses .equals(), not identity. Two distinct list instances
    // with the same content should compare equal so the invalidator is not invoked.
    val original = listOf(1, 2, 3)
    val newButEqual = listOf(1, 2, 3)
    assertNotSame(original, newButEqual)
    val state = PeekableMutableState(original)
    var calls = 0
    state.setWithInvalidator(newButEqual) { calls++ }
    assertEquals(0, calls)
  }
}
