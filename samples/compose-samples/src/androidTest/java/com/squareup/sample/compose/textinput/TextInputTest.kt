package com.squareup.sample.compose.textinput

import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher.Companion.expectValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.WaitForIdleAfterTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class TextInputTest {

  @get:Rule val composeRule = createAndroidComposeRule<TextInputActivity>()
  @get:Rule val waitForIdle = WaitForIdleAfterTest

  @OptIn(ExperimentalTestApi::class)
  @Test fun allowsTextEditing() {
    composeRule.onNode(hasSetTextAction()).performTextInput("he")
    composeRule.onNode(hasSetTextAction()).assert(hasEditableTextEqualTo("he"))

    // For some reason performTextInput("llo") is flaky when running all the tests in this module.
    composeRule.onNode(hasSetTextAction())
      .performTextReplacement("hello")
    composeRule.onNode(hasSetTextAction()).assert(hasEditableTextEqualTo("hello"))
  }

  @Test fun swapsText() {
    composeRule.onNode(hasSetTextAction()).performTextInput("hello")
    composeRule.onNode(hasSetTextAction()).assert(hasEditableTextEqualTo("hello"))

    // Swap to empty field.
    composeRule.onNodeWithText("Swap").performClick()

    composeRule.onNode(hasSetTextAction()).assert(hasEditableTextEqualTo(""))
    composeRule.onNodeWithText("hello").assertDoesNotExist()

    composeRule.onNode(hasSetTextAction()).performTextInput("world")
    composeRule.onNode(hasSetTextAction()).assertTextEquals("world")

    // Swap back to first field.
    composeRule.onNodeWithText("Swap").performClick()

    composeRule.onNode(hasSetTextAction()).assert(hasEditableTextEqualTo("hello"))
    composeRule.onNodeWithText("world").assertDoesNotExist()
  }

  private fun hasEditableTextEqualTo(text: String) =
    expectValue(EditableText, AnnotatedString(text))
}
