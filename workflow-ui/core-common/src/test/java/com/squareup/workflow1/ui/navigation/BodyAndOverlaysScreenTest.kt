package com.squareup.workflow1.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.compatible
import org.junit.Test

internal class BodyAndOverlaysScreenTest {
  data class S<T>(val value: T) : Screen
  data class O<T>(val value: T) : Overlay

  @Test fun mapBody() {
    val before = BodyAndOverlaysScreen(S("s-before"), listOf(O("o-before")), name = "fnord")
    val after = before.mapBody {
      assertThat(it.value).isEqualTo("s-before")
      S(25)
    }

    assertThat(after.body.value).isEqualTo(25)
    assertThat(after.overlays).hasSize(1)
    assertThat(after.overlays.first()).isSameInstanceAs(before.overlays[0])
    assertThat(after.name).isEqualTo("fnord")
    assertThat(compatible(before, after)).isTrue()
  }

  @Test fun mapOverlays() {
    val before = BodyAndOverlaysScreen(S("s-before"), listOf(O("o-before")), name = "bagel")
    val after = before.mapOverlays {
      assertThat(it.value).isEqualTo("o-before")
      O(25)
    }

    assertThat(after.body).isSameInstanceAs(before.body)
    assertThat(after.overlays).hasSize(1)
    assertThat(after.overlays.first().value).isEqualTo(25)
    assertThat(after.name).isEqualTo("bagel")
    assertThat(compatible(before, after)).isTrue()
  }

  @Test fun nameAffectsCompatibility() {
    val unnamed = BodyAndOverlaysScreen<Screen, Overlay>(S(1))
    val alsoUnnamed = BodyAndOverlaysScreen<Screen, Overlay>(S("string"))
    val named = BodyAndOverlaysScreen<Screen, Overlay>(S(1), name = "name1")
    val alsoNamed = BodyAndOverlaysScreen<Screen, Overlay>(S("string"), name = "name2")

    assertThat(compatible(unnamed, alsoUnnamed)).isTrue()
    assertThat(compatible(unnamed, named)).isFalse()
    assertThat(compatible(named, alsoNamed)).isFalse()
  }
}
