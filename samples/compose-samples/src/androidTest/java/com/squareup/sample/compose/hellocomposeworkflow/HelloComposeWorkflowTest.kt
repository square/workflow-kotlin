package com.squareup.sample.compose.hellocomposeworkflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.WaitForIdleAfterTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class HelloComposeWorkflowTest {

  @get:Rule val composeRule = createAndroidComposeRule<HelloComposeWorkflowActivity>()
  @get:Rule val waitForIdle = WaitForIdleAfterTest

  @Test fun togglesBetweenStates() {
    composeRule.onNodeWithText("Hello")
      .assertIsDisplayed()
      .performClick()
    composeRule.onNodeWithText("Goodbye")
      .assertIsDisplayed()
      .performClick()
    composeRule.onNodeWithText("Hello")
      .assertIsDisplayed()
  }
}
