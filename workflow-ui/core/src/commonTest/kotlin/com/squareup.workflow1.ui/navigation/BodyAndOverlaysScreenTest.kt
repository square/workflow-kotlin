package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compatible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class BodyAndOverlaysScreenTest {
  data class S<T>(val value: T) : Screen
  data class O<T>(val value: T) : Overlay

  @Test fun mapBody() {
    val before = BodyAndOverlaysScreen(S("s-before"), listOf(O("o-before")), name = "fnord")
    val after = before.mapBody {
      assertEquals("s-before", it.value)
      S(25)
    }

    assertEquals(25, after.body.value)
    assertEquals(1, after.overlays.size)
    assertSame(before.overlays[0], after.overlays.first())
    assertEquals("fnord", after.name)
    assertTrue { compatible(before, after) }
  }

  @Test fun mapOverlays() {
    val before = BodyAndOverlaysScreen(S("s-before"), listOf(O("o-before")), name = "bagel")
    val after = before.mapOverlays {
      assertEquals("o-before", it.value)
      O(25)
    }

    assertSame(before.body, after.body)
    assertEquals(1, after.overlays.size)
    assertEquals(25, after.overlays.first().value)
    assertEquals("bagel", after.name)
    assertTrue { compatible(before, after) }
  }

  @Test fun nameAffectsCompatibility() {
    val unnamed = BodyAndOverlaysScreen<Screen, Overlay>(S(1))
    val alsoUnnamed = BodyAndOverlaysScreen<Screen, Overlay>(S("string"))
    val named = BodyAndOverlaysScreen<Screen, Overlay>(S(1), name = "name1")
    val alsoNamed = BodyAndOverlaysScreen<Screen, Overlay>(S("string"), name = "name2")

    assertTrue { compatible(unnamed, alsoUnnamed) }
    assertFalse { compatible(unnamed, named) }
    assertFalse { compatible(named, alsoNamed) }
  }
}
