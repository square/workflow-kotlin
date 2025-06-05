package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
public fun SandboxBackground(content: @Composable () -> Unit) {
  var scale by remember { mutableStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier = Modifier
      .wrapContentSize(unbounded = true, align = Alignment.TopStart) // this allows the content to be larger than the initial screen of the app
      .pointerInput(Unit) { // this allows for user's panning to view different parts of content
        awaitEachGesture {
          val event = awaitPointerEvent()

          // zooming
          if (event.type == PointerEventType.Scroll) {
            val scrollDelta = event.changes.first().scrollDelta.y
            scale *= if (scrollDelta < 0) 1.1f else 0.9f
            scale = scale.coerceIn(0.1f, 10f)
            event.changes.forEach { it.consume() }
          }

          // panning: this tracks multiple events within one gesture to see what the user is doing, then calculates the offset and pans the screen accordingly
          val drag = event.changes.firstOrNull()
          if (drag != null && drag.pressed) {
            var prev = drag.position
            while (true) {
              val nextEvent = awaitPointerEvent()
              val nextDrag = nextEvent.changes.firstOrNull() ?: break
              if (!nextDrag.pressed) break

              val delta = nextDrag.position - prev
              offset += delta
              prev = nextDrag.position
              nextDrag.consume()
            }
          }
        }
      }
      .graphicsLayer {
        translationX = offset.x
        translationY = offset.y
        scaleX = scale
        scaleY = scale
      }
  ) {
    Box {
      content() // this is main content
    }
  }
}
