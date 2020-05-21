/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sample.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.foundation.AdapterList
import androidx.ui.foundation.Text
import androidx.ui.material.ListItem
import androidx.ui.material.MaterialTheme
import androidx.ui.material.Scaffold
import androidx.ui.material.TopAppBar
import androidx.ui.material.darkColorPalette
import androidx.ui.res.stringResource
import androidx.ui.tooling.preview.Preview
import com.squareup.sample.R

@Composable fun SampleLauncherApp() {
  val context = ContextAmbient.current
  MaterialTheme(colors = darkColorPalette()) {
    Scaffold(
        topAppBar = {
          TopAppBar(title = {
            Text(stringResource(R.string.app_name))
          })
        }
    ) {
      AdapterList(samples) { sample ->
        ListItem(
            text = sample.name,
            secondaryText = sample.description,
            singleLineSecondaryText = false,
            onClick = { context.launchSample(sample) }
        )
      }
    }
  }
}

@Preview @Composable private fun SampleLauncherAppPreview() {
  SampleLauncherApp()
}

private fun Context.launchSample(sample: Sample) {
  val intent = Intent(this, sample.activityClass.java)
  startActivity(intent)
}
