package com.squareup.sample.compose.launcher

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.sample.compose.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.WaitForIdleAfterTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class SampleLauncherTest {

  @get:Rule val composeRule = createAndroidComposeRule<SampleLauncherActivity>()
  @get:Rule val waitForIdle = WaitForIdleAfterTest

  @OptIn(ExperimentalTestApi::class)
  @Test fun allSamplesLaunch() {
    val appName =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)
    composeRule.onNodeWithText(appName).assertIsDisplayed()

    samples.forEachIndexed { index, sample ->
      try {
        // On smaller screens, we might have so many samples that we need to scroll.
        composeRule.onNode(hasScrollToIndexAction())
          .performScrollToIndex(index)
        composeRule.onNodeWithText(sample.description)
          .performClick()
        pressBack()
      } catch (e: Throwable) {
        throw AssertionError("Failed to launch sample ${sample.name}", e)
      }
    }
  }
}
