package com.squareup.sample.compose.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.WaitForIdleAfterTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class PreviewTest {

  @get:Rule val composeRule = createAndroidComposeRule<PreviewActivity>()
  @get:Rule val waitForIdle = WaitForIdleAfterTest

  @Test fun showsPreviewRendering() {
    composeRule.onNodeWithText(ContactDetailsRendering::class.java.simpleName, substring = true)
      .assertIsDisplayed()
      .assertTextContains(previewContactRendering.details.phoneNumber, substring = true)
      .assertTextContains(previewContactRendering.details.address, substring = true)
  }
}
