package com.squareup.workflow1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class RenderingAndSnapshotTest {
  @Test fun destructuring() {
    val snapshot = TreeSnapshot(Snapshot.of(0)) { emptyMap() }
    val (r, t) = RenderingAndSnapshot("Rendering", snapshot)
    assertEquals(r, "Rendering")
    assertSame(t, snapshot)
  }

  @Test fun `identity equality`() {
    val snapshot = TreeSnapshot(Snapshot.of(0)) { emptyMap() }
    val me = RenderingAndSnapshot("Rendering", snapshot)
    val you = RenderingAndSnapshot("Rendering", snapshot)

    assertEquals(me, me)
    assertNotEquals(me, you)
  }
}
