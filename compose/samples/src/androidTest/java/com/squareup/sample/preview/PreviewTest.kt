package com.squareup.sample.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewTest {

  @get:Rule val composeRule = createAndroidComposeRule<PreviewActivity>()

  @Test fun showsPreviewRendering() {
    composeRule.onNodeWithText(ContactDetailsRendering::class.java.simpleName, substring = true)
      .assertIsDisplayed()
      .assertTextContains(previewContactRendering.details.phoneNumber)
      .assertTextContains(previewContactRendering.details.address)
  }
}
