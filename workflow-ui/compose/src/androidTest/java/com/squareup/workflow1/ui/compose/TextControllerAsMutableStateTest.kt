package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class TextControllerAsMutableStateTest {

  private val composeRule = createComposeRule()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @Test fun setTextInCompose() {
    val textController = TextController()
    composeRule.setContent {
      var state by textController.asMutableTextFieldValueState()
      LaunchedEffect(Unit) {
        state = TextFieldValue(text = "foo")
      }
    }
    composeRule.runOnIdle {
      assertThat(textController.textValue).isEqualTo("foo")
    }
  }

  @Test fun setTextInComposeWithSelection() {
    val textController = TextController()
    val textFieldValue = mutableStateOf<TextFieldValue?>(null)
    composeRule.setContent {
      var state by textController.asMutableTextFieldValueState()
      LaunchedEffect(Unit) {
        state = TextFieldValue(text = "foobar", selection = TextRange(1, 3))
      }
      LaunchedEffect(Unit) {
        snapshotFlow { state }
          .collect {
            textFieldValue.value = it
          }
      }
    }
    composeRule.runOnIdle {
      assertThat(textController.textValue).isEqualTo("foobar")
      assertThat(textFieldValue.value).isEqualTo(
        TextFieldValue(
          text = "foobar",
          selection = TextRange(1, 3)
        )
      )
    }
  }

  @Test fun setTextViaTextController() {
    val textController = TextController()
    val textFieldValue = mutableStateOf<TextFieldValue?>(null)
    composeRule.setContent {
      val state by textController.asMutableTextFieldValueState()
      LaunchedEffect(Unit) {
        snapshotFlow { state }
          .collect {
            textFieldValue.value = it
          }
      }
    }
    textController.textValue = "foo"
    composeRule.runOnIdle {
      assertThat(textFieldValue.value).isEqualTo(
        TextFieldValue(
          text = "foo",
          selection = TextRange(3)
        )
      )
    }
  }

  @Test fun withInitialSelectionSet() {
    val textController = TextController("foobar")
    val textFieldValue = mutableStateOf<TextFieldValue?>(null)
    composeRule.setContent {
      val state by textController.asMutableTextFieldValueState(
        initialSelection = TextRange(
          start = 1,
          end = 3,
        ),
      )
      LaunchedEffect(Unit) {
        snapshotFlow { state }
          .collect {
            textFieldValue.value = it
          }
      }
    }
    composeRule.runOnIdle {
      assertThat(textFieldValue.value).isEqualTo(
        TextFieldValue(
          text = "foobar",
          selection = TextRange(1, 3)
        )
      )
    }
  }
}
