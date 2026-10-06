package com.squareup.sample.runtimelibrary.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppTest {

  // TODO Migrate to the androidx.compose.ui.test.junit4.v2 rules, which use StandardTestDispatcher.
  @Suppress("DEPRECATION") @get:Rule val rule = createAndroidComposeRule<AppActivity>()

  @Test
  fun appStarts() {
    rule.onNodeWithText("Hello").assertIsDisplayed()
  }
}
