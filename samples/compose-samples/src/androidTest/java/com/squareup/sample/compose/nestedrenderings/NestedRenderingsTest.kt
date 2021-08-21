package com.squareup.sample.compose.nestedrenderings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ADD_BUTTON_TEXT = "Add Child"

@RunWith(AndroidJUnit4::class)
class NestedRenderingsTest {

  @OptIn(WorkflowUiExperimentalApi::class)
  @get:Rule val composeRule = createAndroidComposeRule<NestedRenderingsActivity>()

  @Test fun childrenAreAddedAndRemoved() {
    composeRule.onNodeWithText(ADD_BUTTON_TEXT)
      .assertIsDisplayed()
      .performClick()

    composeRule.onAllNodesWithText(ADD_BUTTON_TEXT)
      .assertCountEquals(2)
      .forEach { it.performClick() }

    composeRule.onAllNodesWithText(ADD_BUTTON_TEXT)
      .assertCountEquals(4)

    resetAll()
    composeRule.onAllNodesWithText(ADD_BUTTON_TEXT).assertCountEquals(1)
  }

  /**
   * We can't rely on the order of nodes returned by [onAllNodesWithText], and the contents of the
   * collection will change as we remove nodes, so we have to double-loop over all reset buttons and
   * click them all until there is only one left.
   */
  private fun resetAll() {
    var foundNodes = Int.MAX_VALUE
    while (foundNodes > 1) {
      foundNodes = 0
      composeRule.onAllNodesWithText("Reset").forEach {
        try {
          it.assertExists()
        } catch (e: AssertionError) {
          // No more reset buttons, we're done.
          return@forEach
        }
        foundNodes++
        it.performClick()
      }
    }
  }

  private fun SemanticsNodeInteractionCollection.forEach(
    block: (SemanticsNodeInteraction) -> Unit
  ) {
    val count = fetchSemanticsNodes().size
    for (i in 0 until count) block(get(i))
  }
}
