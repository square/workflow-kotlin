package com.squareup.sample.compose.inlinerendering

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
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
class InlineRenderingTest {

  private val composeRule = createAndroidComposeRule<InlineRenderingActivity>()
  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)

  @Test fun counterIncrements() {
    composeRule.onNode(hasClickAction())
      .assertTextEquals("Counter: ", "0")
      .assertIsDisplayed()
      .performClick()

    composeRule.onNode(hasClickAction())
      .assertTextEquals("Counter: ", "1")
      .assertIsDisplayed()
  }

  @Test fun counterAnimates() {
    // Take manual control of animations.
    composeRule.mainClock.autoAdvance = false

    composeRule.onNode(hasClickAction())
      .performClick()

    composeRule.mainClock.advanceTimeByFrame()

    // During the animation, both counter values will be present.
    composeRule.onNode(hasClickAction())
      .assertTextEquals("Counter: ", "0", "1")
  }
}
