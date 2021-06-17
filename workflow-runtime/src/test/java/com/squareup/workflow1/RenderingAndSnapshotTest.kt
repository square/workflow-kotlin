package com.squareup.workflow1

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class RenderingAndSnapshotTest {
  @Test fun destructuring() {
    val snapshot = TreeSnapshot(Snapshot.of(0)) { emptyMap() }
    val (r, t, w) = RenderingAndSnapshot("Rendering", snapshot, workInProgress = true)
    assertEquals(r, "Rendering")
    assertSame(t, snapshot)
    assertTrue(w)
  }

  @Test fun `identity equality`() {
    val snapshot = TreeSnapshot(Snapshot.of(0)) { emptyMap() }
    val me = RenderingAndSnapshot("Rendering", snapshot, workInProgress = true)
    val you = RenderingAndSnapshot("Rendering", snapshot, workInProgress = true)

    assertEquals(me, me)
    assertNotEquals(me, you)
  }
}
