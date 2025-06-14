package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.Node

/**
 * A trace tab selector that allows devs to switch between different states within the provided trace.
 */
@Composable
public fun StateSelectTab(
  frames: List<Node>,
  currentIndex: Int,
  onIndexChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val state = rememberLazyListState()

  Surface(
    modifier = modifier
      .padding(4.dp),
    color = Color.White,
  ) {
    LazyRow(
      modifier = Modifier
        .padding(8.dp),
      state = state
    ) {
      items(frames.size) { index ->
        Text(
          text = "State ${index + 1}",
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
