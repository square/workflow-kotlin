package com.squareup.sample.compose.textinput

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.DetectLeaksAfterTestSuccess
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class TextInputTest {

  private val composeRule = createAndroidComposeRule<TextInputActivity>()
  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @OptIn(ExperimentalTestApi::class)
  @Test fun allowsTextEditing() {
    composeRule.onNode(hasSetTextAction()).performTextInput("he")
    composeRule.onNode(hasSetTextAction()).assertTextEquals("he")

    // For some reason performTextInput("llo") is flaky when running all the tests in this module.
    composeRule.onNode(hasSetTextAction())
      .performTextReplacement("hello")
    composeRule.onNode(hasSetTextAction()).assertTextEquals("hello")
  }

  @Test fun swapsText() {
    composeRule.onNode(hasSetTextAction()).performTextInput("hello")
    composeRule.onNode(hasSetTextAction()).assertTextEquals("hello")

    // Swap to empty field.
    composeRule.onNodeWithText("Swap").performClick()

    // The EditableText is empty, but it's showing a hint of `Enter some text`.
    // Even though the actual EditableText is blank/empty, if it's included, the assertion fails.
    composeRule.onNode(hasSetTextAction())
      .assertTextEquals("Enter some text", includeEditableText = false)
    composeRule.onNodeWithText("hello").assertDoesNotExist()

    composeRule.onNode(hasSetTextAction()).performTextInput("world")
    composeRule.onNode(hasSetTextAction()).assertTextEquals("world")

    // Swap back to first field.
    composeRule.onNodeWithText("Swap").performClick()

    composeRule.onNode(hasSetTextAction()).assertTextEquals("hello")
    composeRule.onNodeWithText("world").assertDoesNotExist()
  }
}
