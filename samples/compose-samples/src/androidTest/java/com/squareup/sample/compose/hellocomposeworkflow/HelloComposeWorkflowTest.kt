package com.squareup.sample.compose.hellocomposeworkflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelloComposeWorkflowTest {

  private val composeRule = createAndroidComposeRule<HelloComposeWorkflowActivity>()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @Test fun togglesBetweenStates() {
    composeRule.activityRule.scenario

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
