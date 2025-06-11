package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * This is the backdrop for the whole app. Since there can be hundreds of modules at a time, there
 * is not realistic way to fit everything on the screen at once. Having the liberty to pan across
 * the whole tree as well as zoom into specific subtrees means there's a lot more control when
 * analyzing the traces.
 *
 */
@Composable
public fun SandboxBackground(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        // Panning capabilities: watches for drag gestures and applies the translation
        detectDragGestures { _, translation->
          offset += translation
        }
      }
      .pointerInput(Unit) {
        // Zooming capabilities: watches for any scroll events and immediately consumes changes.
        // - This is AI generated.
        awaitEachGesture {
          val event = awaitPointerEvent()
          if (event.type == PointerEventType.Scroll) {
            val scrollDelta = event.changes.first().scrollDelta.y
            scale *= if (scrollDelta < 0) 1.1f else 0.9f
            scale = scale.coerceIn(0.1f, 10f)
            event.changes.forEach { it.consume() }
          }
        }
      }
  ) {
    Box(
      modifier = Modifier
        .wrapContentSize(unbounded = true, align = Alignment.Center)
        .graphicsLayer {
          translationX = offset.x
          translationY = offset.y
          scaleX = scale
          scaleY = scale
        }
    ) {
      content()
    }
  }
}
