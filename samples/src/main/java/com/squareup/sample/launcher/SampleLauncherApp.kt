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

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.Composable
import androidx.compose.remember
import androidx.core.app.ActivityOptionsCompat.makeScaleUpAnimation
import androidx.core.content.ContextCompat.startActivity
import androidx.ui.core.AndroidOwner
import androidx.ui.core.Modifier
import androidx.ui.core.OwnerAmbient
import androidx.ui.core.Ref
import androidx.ui.core.globalBounds
import androidx.ui.core.onPositioned
import androidx.ui.foundation.AdapterList
import androidx.ui.foundation.Text
import androidx.ui.material.ListItem
import androidx.ui.material.MaterialTheme
import androidx.ui.material.Scaffold
import androidx.ui.material.TopAppBar
import androidx.ui.material.darkColorPalette
import androidx.ui.res.stringResource
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.PxBounds
import androidx.ui.unit.height
import androidx.ui.unit.width
import com.squareup.sample.R

@Composable fun SampleLauncherApp() {
  MaterialTheme(colors = darkColorPalette()) {
    Scaffold(
        topAppBar = {
          TopAppBar(title = {
            Text(stringResource(R.string.app_name))
          })
        }
    ) {
      AdapterList(samples) { sample ->
        SampleItem(sample)
      }
    }
  }
}

@Preview @Composable private fun SampleLauncherAppPreview() {
  SampleLauncherApp()
}

@Composable private fun SampleItem(sample: Sample) {
  // See https://issuetracker.google.com/issues/156875705.
  @Suppress("DEPRECATION")
  val rootView = (OwnerAmbient.current as AndroidOwner).view

  /**
   * [androidx.ui.core.LayoutCoordinates.globalBounds] corresponds to the coordinates in the root
   * Android view hosting the composition.
   */
  val globalBounds = remember { Ref<PxBounds>() }

  ListItem(
      text = sample.name,
      secondaryText = sample.description,
      singleLineSecondaryText = false,
      modifier = Modifier.onPositioned { globalBounds.value = it.globalBounds },
      onClick = { launchSample(sample, rootView, globalBounds.value) }
  )
}

private fun launchSample(
  sample: Sample,
  rootView: View,
  sourceBounds: PxBounds?
) {
  val context = rootView.context
  val intent = Intent(context, sample.activityClass.java)
  val options: Bundle? = sourceBounds?.let {
    makeScaleUpAnimation(
        rootView,
        it.left.value.toInt(),
        it.top.value.toInt(),
        it.width.value.toInt(),
        it.height.value.toInt()
    ).toBundle()
  }
  startActivity(context, intent, options)
}
