package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.WorkflowNode

@Composable
public fun StateSelectTab(
  trace: List<WorkflowNode>,
  currentIndex: Int,
  onIndexChange: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val state = rememberLazyListState()

  LazyRow(
    modifier = modifier,
    state = state
  ) {
    items(trace.size) { index ->
      Button(
        modifier = Modifier
          .padding(10.dp),
        onClick = { onIndexChange(index) },
        colors = ButtonDefaults.buttonColors(
          backgroundColor = if (index == currentIndex) Color.DarkGray else Color.LightGray
        )
      ) {
        Text("State ${index + 1}")
      }
    }
  }
}
