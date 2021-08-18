package com.squareup.sample.compose.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.sample.compose.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleLauncherTest {

  @get:Rule val composeRule = createAndroidComposeRule<SampleLauncherActivity>()

  @Test fun allSamplesLaunch() {
    val appName =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)
    composeRule.onNodeWithText(appName).assertIsDisplayed()

    samples.forEach { sample ->
      try {
        composeRule.onNodeWithText(sample.description, useUnmergedTree = true)
          .performClick()
        pressBack()
      } catch (e: Throwable) {
        throw AssertionError("Failed to launch sample ${sample.name}", e)
      }
    }
  }
}
