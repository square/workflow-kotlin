package com.squareup.workflow1.internal.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.squareup.workflow1.internal.compose.enableImmediateApplyForTests
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SynchronizedMoleculeTest {

  @BeforeTest fun setUp() {
    enableImmediateApplyForTests()
  }

  @Test fun first_recompose_runs_content_and_returns_its_value() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(onNeedsRecomposition = {})
    try {
      val result = molecule.recomposeWithContent { 42 }
      assertEquals(42, result)
    } finally {
      molecule.close()
    }
  }

  @Test fun recomposeWithContent_returns_latest_value_for_each_call() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(onNeedsRecomposition = {})
    try {
      // Each call gets a fresh content lambda. The molecule's internal `content` field is
      // backed by mutableStateOf, so each new lambda triggers a recomposition.
      assertEquals(1, molecule.recomposeWithContent { 1 })
      assertEquals(2, molecule.recomposeWithContent { 2 })
      assertEquals(3, molecule.recomposeWithContent { 3 })
    } finally {
      molecule.close()
    }
  }

  @Test fun needsRecomposition_is_false_when_nothing_changed() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(onNeedsRecomposition = {})
    try {
      molecule.recomposeWithContent { "noop" }
      assertFalse(molecule.needsRecomposition)
    } finally {
      molecule.close()
    }
  }

  @Test fun second_recompose_picks_up_state_changes_made_between_calls() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(onNeedsRecomposition = {})
    try {
      var state by mutableStateOf("first")
      val content: @Composable () -> String = { state }
      assertEquals("first", molecule.recomposeWithContent(content))
      state = "second"
      assertEquals("second", molecule.recomposeWithContent(content))
    } finally {
      molecule.close()
    }
  }

  @Test fun close_makes_needsRecomposition_return_false() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(onNeedsRecomposition = {})
    molecule.recomposeWithContent { Unit }
    molecule.close()
    assertFalse(molecule.needsRecomposition)
  }

  @Test fun composition_throwing_propagates_from_recomposeWithContent() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(onNeedsRecomposition = {})
    try {
      molecule.recomposeWithContent { Unit }
      val state = mutableStateOf(false)
      val content: @Composable () -> Int = {
        if (state.value) error("oops")
        0
      }
      molecule.recomposeWithContent(content)
      state.value = true
      assertFailsWith<IllegalStateException> {
        molecule.recomposeWithContent(content)
      }
    } finally {
      molecule.close()
    }
  }
}
