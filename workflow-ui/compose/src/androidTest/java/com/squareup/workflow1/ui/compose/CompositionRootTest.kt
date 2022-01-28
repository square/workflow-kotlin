package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.DetectLeaksAfterTestSuccess
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
internal class CompositionRootTest {

  private val composeRule = createComposeRule()
  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)

  @Test fun wrappedWithRootIfNecessary_wrapsWhenNecessary() {
    val root: CompositionRoot = { content ->
      Column {
        BasicText("one")
        content()
      }
    }

    composeRule.setContent {
      WrappedWithRootIfNecessary(root) {
        BasicText("two")
      }
    }

    // These semantics used to merge, but as of dev15, they don't, which seems to be a bug.
    // https://issuetracker.google.com/issues/161979921
    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun wrappedWithRootIfNecessary_onlyWrapsOnce() {
    val root: CompositionRoot = { content ->
      Column {
        BasicText("one")
        content()
      }
    }

    composeRule.setContent {
      WrappedWithRootIfNecessary(root) {
        BasicText("two")
        WrappedWithRootIfNecessary(root) {
          BasicText("three")
        }
      }
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
    composeRule.onNodeWithText("three").assertIsDisplayed()
  }

  @Test fun wrappedWithRootIfNecessary_seesUpdatesFromRootWrapper() {
    val wrapperText = mutableStateOf("one")
    val root: CompositionRoot = { content ->
      Column {
        BasicText(wrapperText.value)
        content()
      }
    }

    composeRule.setContent {
      WrappedWithRootIfNecessary(root) {
        BasicText("two")
      }
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
    wrapperText.value = "ENO"
    composeRule.onNodeWithText("ENO").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun wrappedWithRootIfNecessary_rewrapsWhenDifferentRoot() {
    val root1: CompositionRoot = { content ->
      Column {
        BasicText("one")
        content()
      }
    }
    val root2: CompositionRoot = { content ->
      Column {
        BasicText("ENO")
        content()
      }
    }
    val viewEnvironment = mutableStateOf(root1)

    composeRule.setContent {
      WrappedWithRootIfNecessary(viewEnvironment.value) {
        BasicText("two")
      }
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
    viewEnvironment.value = root2
    composeRule.onNodeWithText("ENO").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }
}
