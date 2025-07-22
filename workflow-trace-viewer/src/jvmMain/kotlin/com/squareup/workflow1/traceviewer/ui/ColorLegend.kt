package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.NodeState

/**
 * Simple UI displaying the color legend for the different node states in the trace
 */
@Composable
fun ColorLegend(
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.padding(8.dp)
    ) {
      NodeState.entries.forEach { state ->
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(vertical = 2.dp)
        ) {
          Box(
            modifier = Modifier
              .size(16.dp)
              .background(
                color = state.color,
              )
              .then(
                if (state.color == Color.Transparent) {
                  modifier.border(1.dp, Color.Gray)
                } else {
                  modifier
                }
              )
          )
          Text(
            text = state.name,
            fontStyle = FontStyle.Italic,
          )
        }
      }
    }
  }
}
