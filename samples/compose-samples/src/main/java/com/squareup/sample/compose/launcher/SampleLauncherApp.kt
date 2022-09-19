package com.squareup.sample.compose.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass.Initial
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat.makeScaleUpAnimation
import androidx.core.content.ContextCompat.startActivity
import com.squareup.sample.compose.R

@Composable fun SampleLauncherApp() {
  MaterialTheme(colors = darkColors()) {
    Scaffold(
      topBar = {
        TopAppBar(title = {
          Text(stringResource(R.string.app_name))
        })
      }
    ) { padding ->
      LazyColumn(Modifier.padding(padding)) {
        items(samples) {
          SampleItem(it)
        }
      }
    }
  }
}

@Preview @Composable
private fun SampleLauncherAppPreview() {
  SampleLauncherApp()
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SampleItem(sample: Sample) {
  val rootView = LocalView.current

  /**
   * [androidx.compose.ui.layout.LayoutCoordinates.globalBounds] corresponds to the coordinates in
   * the root Android view hosting the composition.
   */
  val globalBounds = remember { Ref<Rect>() }

  ListItem(
    text = { Text(sample.name) },
    secondaryText = { Text(sample.description) },
    singleLineSecondaryText = false,
    // Animate the activities as scaling up from where the preview is drawn.
    icon = { SamplePreview(sample) { globalBounds.value = it.boundsInRoot() } },
    modifier = Modifier.clickable { launchSample(sample, rootView, globalBounds.value) }
  )
}

@Composable private fun SamplePreview(
  sample: Sample,
  onPreviewCoordinates: (LayoutCoordinates) -> Unit
) {
  val configuration = LocalConfiguration.current
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
      .onGloballyPositioned(onPreviewCoordinates)
  ) {
    // Preview the samples with a light theme, since that's what most of them use.
    MaterialTheme(lightColors()) {
      Surface {
        Box(
          modifier = Modifier
            // This preview isn't meant to be interactive.
            .disableTouchInput()
            // Measure/layout the child at full screen size, and then just scale the pixels
            // down. This way all the text and other density-dependent things get scaled
            // correctly too.
            .requiredHeight(configuration.screenHeightDp.dp)
            .requiredWidth(configuration.screenWidthDp.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
        ) {
          sample.preview()
        }
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

private fun Modifier.disableTouchInput(): Modifier = pointerInput(Unit) {
  forEachGesture {
    awaitPointerEventScope {
      awaitPointerEvent(Initial).let { event ->
        event.changes.forEach { change ->
          change.consume()
        }
      }
    }
  }
}
