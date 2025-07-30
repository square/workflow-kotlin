package com.squareup.workflow1.traceviewer.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.squareup.workflow1.traceviewer.SandboxState

/**
 * This is the backdrop for the whole app. Since there can be hundreds of modules at a time, there
 * is not realistic way to fit everything on the screen at once. Having the liberty to pan across
 * the whole tree as well as zoom into specific subtrees means there's a lot more control when
 * analyzing the traces.
 *
 */
@Composable
internal fun SandboxBackground(
  appWindowSize: IntSize,
  sandboxState: SandboxState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        // Panning capabilities: watches for drag gestures and applies the translation
        detectDragGestures { _, translation ->
          sandboxState.offset += translation
        }
      }
      .pointerInput(Unit) {
        // Zooming capabilities: watches for any scroll events and immediately consumes changes.
        // - This is AI generated.
        awaitEachGesture {
          val event = awaitPointerEvent()
          if (event.type == PointerEventType.Scroll) {
            val pointerInput = event.changes.first()
            val pointerOffsetToCenter = Offset(
              // For some reason using 1.5 made zooming more natural than 2
              x = pointerInput.position.x - appWindowSize.width / (3/2),
              y = pointerInput.position.y - appWindowSize.height / 2
            )
            val scrollDelta = pointerInput.scrollDelta.y
            // Applies zoom factor based on the actual delta change rather than just the act of scrolling
            // This helps to normalize mouse scrolling and touchpad scrolling, since touchpad will
            // fire a lot more scroll events.
            val factor = 1f + (-scrollDelta * 0.1f)
            val minWindowSize = 0.1f
            val maxWindowSize = 2f
            val oldScale = sandboxState.scale
            val newScale = (oldScale * factor).coerceIn(minWindowSize, maxWindowSize)
            val scaleRatio = newScale / oldScale

            val newOrigin = sandboxState.offset - pointerOffsetToCenter
            val scaledView = newOrigin * scaleRatio
            val resetViewOffset = scaledView + pointerOffsetToCenter
            sandboxState.offset = resetViewOffset
            sandboxState.scale = newScale

            event.changes.forEach { it.consume() }
          }
        }
      }
  ) {
    Box(
      modifier = Modifier
        .wrapContentSize(unbounded = true, align = Alignment.Center)
        .graphicsLayer {
          translationX = sandboxState.offset.x
          translationY = sandboxState.offset.y
          scaleX = sandboxState.scale
          scaleY = sandboxState.scale
        }
    ) {
      content()
    }
  }
}
