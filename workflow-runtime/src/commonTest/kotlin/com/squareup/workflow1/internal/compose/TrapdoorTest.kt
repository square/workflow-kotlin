package com.squareup.workflow1.internal.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

internal class TrapdoorTest {

  @Test
  fun open_block_form_passes_a_trapdoor_into_block() = runTest {
    var captured: Trapdoor? = null
    val test = TestComposition(backgroundScope) { Trapdoor.open { door -> captured = door } }
    try {
      test.recompose()
      assertNotNull(captured)
    } finally {
      test.close()
    }
  }

  @Test
  fun inMovableGroup_returns_value_from_content() = runTest {
    val test =
      TestComposition(backgroundScope) {
        Trapdoor.open { door -> door.inMovableGroup(key = 1, dataKey = "k") { 42 } }
      }
    try {
      assertEquals(42, test.recompose())
    } finally {
      test.close()
    }
  }

  @Test
  fun inMovableGroup_with_two_data_keys_returns_value_from_content() = runTest {
    val test =
      TestComposition(backgroundScope) {
        Trapdoor.open { door ->
          door.inMovableGroup(key = 1, dataKey1 = "a", dataKey2 = "b") { "ok" }
        }
      }
    try {
      assertEquals("ok", test.recompose())
    } finally {
      test.close()
    }
  }
}
