package com.squareup.sample.compose.inlinerendering

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineRenderingTest {

  @get:Rule val composeRule = createAndroidComposeRule<InlineRenderingActivity>()

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
