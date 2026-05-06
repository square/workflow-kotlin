package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.squareup.workflow1.internal.compose.Trapdoor.Companion.runIfValueChanged
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class TrapdoorTest {

  @Test fun open_block_form_passes_a_trapdoor_into_block() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var captured: Trapdoor? = null
      test.recompose {
        Trapdoor.open { door -> captured = door }
      }
      assertNotNull(captured)
    } finally {
      test.close()
    }
  }

  @Test fun open_function_form_returns_a_trapdoor() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val door: Trapdoor = test.recompose { Trapdoor.open() }
      assertNotNull(door)
    } finally {
      test.close()
    }
  }

  @Test fun inMovableGroup_returns_value_from_content() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val result = test.recompose {
        Trapdoor.open { door ->
          door.inMovableGroup(key = 1, dataKey = "k") { 42 }
        }
      }
      assertEquals(42, result)
    } finally {
      test.close()
    }
  }

  @Test fun inMovableGroup_with_two_data_keys_returns_value_from_content() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val result = test.recompose {
        Trapdoor.open { door ->
          door.inMovableGroup(key = 1, dataKey1 = "a", dataKey2 = "b") { "ok" }
        }
      }
      assertEquals("ok", result)
    } finally {
      test.close()
    }
  }

  @Test fun runIfValueChanged_does_not_call_on_first_compose() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val state = mutableStateOf("a")
      val seen = mutableListOf<String>()
      test.recompose {
        runIfValueChanged(state.value) { old -> seen += old }
      }
      assertContentEquals(emptyList(), seen)
    } finally {
      test.close()
    }
  }

  @Test fun runIfValueChanged_calls_with_previous_value_when_value_changes() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val state = mutableStateOf("a")
      val seen = mutableListOf<String>()
      val content: @Composable () -> Unit = {
        runIfValueChanged(state.value) { old -> seen += old }
      }
      test.recompose(content)
      state.value = "b"
      test.recompose(content)
      assertContentEquals(listOf("a"), seen)

      state.value = "c"
      test.recompose(content)
      assertContentEquals(listOf("a", "b"), seen)
    } finally {
      test.close()
    }
  }

  @Test fun runIfValueChanged_does_not_call_when_value_unchanged_across_recomposition() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      val trigger = mutableStateOf(0)
      val seen = mutableListOf<String>()
      val content: @Composable () -> Unit = {
        // Read trigger so the composable gets invalidated each time it changes.
        trigger.value
        runIfValueChanged("constant") { old -> seen += old }
      }
      test.recompose(content)
      trigger.value = 1
      test.recompose(content)
      trigger.value = 2
      test.recompose(content)
      assertContentEquals(emptyList(), seen)
    } finally {
      test.close()
    }
  }
}
