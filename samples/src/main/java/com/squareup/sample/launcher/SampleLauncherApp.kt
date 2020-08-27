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
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumnFor
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.drawLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.gesture.rawPressStartGestureFilter
import androidx.compose.ui.input.pointer.PointerEventPass.Initial
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.globalBounds
import androidx.compose.ui.node.Ref
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.ConfigurationAmbient
import androidx.compose.ui.platform.ViewAmbient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat.makeScaleUpAnimation
import androidx.core.content.ContextCompat.startActivity
import androidx.ui.tooling.preview.Preview
import com.squareup.sample.R.string

@Composable fun SampleLauncherApp() {
  MaterialTheme(colors = darkColors()) {
    Scaffold(
        topBar = {
          TopAppBar(title = {
            Text(stringResource(string.app_name))
          })
        }
    ) {
      LazyColumnFor(samples) { sample ->
        SampleItem(sample)
      }
    }
  }
}

@Preview @Composable private fun SampleLauncherAppPreview() {
  SampleLauncherApp()
}

@Composable private fun SampleItem(sample: Sample) {
  val rootView = ViewAmbient.current

  /**
   * [androidx.ui.core.LayoutCoordinates.globalBounds] corresponds to the coordinates in the root
   * Android view hosting the composition.
   */
  val globalBounds = remember { Ref<Rect>() }

  ListItem(
      text = { Text(sample.name) },
      secondaryText = { Text(sample.description) },
      singleLineSecondaryText = false,
      // Animate the activities as scaling up from where the preview is drawn.
      icon = { SamplePreview(sample) { globalBounds.value = it.globalBounds } },
      modifier = Modifier.clickable { launchSample(sample, rootView, globalBounds.value) }
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
    MaterialTheme(lightColors()) {
      Surface {
        Box(
            modifier = Modifier
                // Disable touch input, since this preview isn't meant to be interactive.
                .rawPressStartGestureFilter(
                    enabled = true, executionPass = Initial, onPressStart = {}
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
  sourceBounds: Rect?
) {
  val context = rootView.context
  val intent = Intent(context, sample.activityClass.java)
  val options: Bundle? = sourceBounds?.let {
    makeScaleUpAnimation(
        rootView,
        it.left.toInt(),
        it.top.toInt(),
        it.width.toInt(),
        it.height.toInt()
    ).toBundle()
  }
  startActivity(context, intent, options)
}
