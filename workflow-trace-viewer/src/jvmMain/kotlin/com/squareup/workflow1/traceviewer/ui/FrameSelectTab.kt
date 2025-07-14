package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.Node
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A trace tab selector that allows devs to switch between different states within the provided trace.
 */
@Composable
internal fun FrameSelectTab(
  frames: List<Node>,
  currentIndex: Int,
  onIndexChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val lazyListState = rememberLazyListState()

  Surface(
    modifier = modifier,
    color = Color.White,
  ) {
    LazyRow(
      state = lazyListState,
      modifier = Modifier
        .padding(8.dp)
        .pointerInput(Unit) {
          coroutineScope {
            awaitEachGesture {
              val event = awaitPointerEvent()
              if (event.type == PointerEventType.Scroll) {
                val scrollDeltaY = event.changes.first().scrollDelta.y
                launch {
                  lazyListState.scroll(MutatePriority.Default) {
                    scrollBy(scrollDeltaY * 10f)
                  }
                }
              }
            }
          }
        },
    ) {
      items(frames.size) { index ->
        Text(
          text = "Frame ${index + 1}",
          color = if (index == currentIndex) Color.Black else Color.LightGray,
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onIndexChange(index) }
            .padding(10.dp)
        )
      }
    }
  }
}
