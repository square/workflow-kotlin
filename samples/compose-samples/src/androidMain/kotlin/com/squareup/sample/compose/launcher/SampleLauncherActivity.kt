package com.squareup.sample.compose.launcher

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

/**
 * Displays all the available Compose samples in a list, and launches samples when they're clicked.
 */
class SampleLauncherActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleLauncherApp()
    }
  }
}
