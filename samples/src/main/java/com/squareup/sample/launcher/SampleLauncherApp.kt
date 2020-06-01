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
import androidx.ui.core.ConfigurationAmbient
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Modifier
import androidx.ui.core.OwnerAmbient
import androidx.ui.core.PointerEventPass.PreDown
import androidx.ui.core.Ref
import androidx.ui.core.drawLayer
import androidx.ui.core.gesture.rawPressStartGestureFilter
import androidx.ui.core.globalBounds
import androidx.ui.core.onPositioned
import androidx.ui.foundation.AdapterList
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.layout.aspectRatio
import androidx.ui.layout.height
import androidx.ui.layout.width
import androidx.ui.material.ListItem
import androidx.ui.material.MaterialTheme
import androidx.ui.material.Scaffold
import androidx.ui.material.Surface
import androidx.ui.material.TopAppBar
import androidx.ui.material.darkColorPalette
import androidx.ui.material.lightColorPalette
import androidx.ui.res.stringResource
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.PxBounds
import androidx.ui.unit.dp
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
      text = { Text(sample.name) },
      secondaryText = { Text(sample.description) },
      singleLineSecondaryText = false,
      // Animate the activities as scaling up from where the preview is drawn.
      icon = { SamplePreview(sample) { globalBounds.value = it.globalBounds } },
      onClick = { launchSample(sample, rootView, globalBounds.value) }
  )
}

@Composable private fun SamplePreview(
  sample: Sample,
  onPreviewCoordinates: (LayoutCoordinates) -> Unit
) {
  val configuration = ConfigurationAmbient.current
  val screenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
  // 88dp is taken from ListItem implementation. This doesn't seem to be coming in via any
  // constraints as of dev11.
  val previewHeight = 88.dp - 16.dp
  val scale = previewHeight / configuration.screenHeightDp.dp

  // Force the previews to the scaled size, with the aspect ratio of the device.
  // This is needed because the inner Box measures the previews at maximum size, so we have to clamp
  // the measurements here otherwise the rest of the UI will think the previews are full-size even
  // though they're graphically scaled down.
  Box(
      modifier = Modifier
          .height(previewHeight)
          .aspectRatio(screenRatio)
          .onPositioned(onPreviewCoordinates)
  ) {
    // Preview the samples with a light theme, since that's what most of them use.
    MaterialTheme(lightColorPalette()) {
      Surface {
        Box(
            modifier = Modifier
                // Disable touch input, since this preview isn't meant to be interactive.
                .rawPressStartGestureFilter(
                    enabled = true, executionPass = PreDown, onPressStart = {}
                )
                // Measure/layout the child at full screen size, and then just scale the pixels
                // down. This way all the text and other density-dependent things get scaled
                // correctly too.
                .height(configuration.screenHeightDp.dp)
                .width(configuration.screenWidthDp.dp)
                .drawLayer(scaleX = scale, scaleY = scale),
            children = sample.preview
        )
      }
    }
  }
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
