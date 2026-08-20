package com.squareup.workflow1.internal.compose.runtime

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

internal class SynchronizedMoleculeTest {

  @BeforeTest fun setUp() {
    enableImmediateApplyForTests()
  }

  @Test fun first_recompose_runs_content_and_returns_its_value() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(
      onNeedsRecomposition = {},
      content = { 42 }
    )
    try {
      val result = molecule.recompose()
      assertEquals(42, result)
    } finally {
      molecule.close()
    }
  }

  @Test fun needsRecomposition_is_false_when_nothing_changed() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(
      onNeedsRecomposition = {},
      content = { "noop" }
    )
    try {
      molecule.recompose()
      assertFalse(molecule.needsRecomposition)
    } finally {
      molecule.close()
    }
  }

  @Test fun second_recompose_picks_up_state_changes_made_between_calls() = runTest {
    var state by mutableStateOf("first")
    val molecule = backgroundScope.launchSynchronizedMolecule(
      onNeedsRecomposition = {},
      content = { state })
    try {
      assertEquals("first", molecule.recompose())
      state = "second"
      assertEquals("second", molecule.recompose())
    } finally {
      molecule.close()
    }
  }

  @Test fun close_makes_needsRecomposition_return_false() = runTest {
    val molecule = backgroundScope.launchSynchronizedMolecule(
      onNeedsRecomposition = {},
      content = {}
    )
    // molecule.recompose()
    molecule.close()
    assertFalse(molecule.needsRecomposition)
  }

  @Test fun composition_throwing_propagates_from_recomposeWithContent() = runTest {
    var state by mutableStateOf(false)
    val molecule = backgroundScope.launchSynchronizedMolecule(
      onNeedsRecomposition = {},
      content = {
        if (state) error("oops")
        0
      }
    )
    try {
      molecule.recompose()
      molecule.recompose()
      state = true
      assertFailsWith<IllegalStateException> {
        molecule.recompose()
      }
    } finally {
      molecule.close()
    }
  }
}
