package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.workflow1.traceviewer.TraceMode

@Composable
internal fun TraceModeToggleSwitch(
  onToggle: () -> Unit,
  traceMode: TraceMode,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Switch(
      checked = traceMode is TraceMode.Live,
      onCheckedChange = {
        onToggle()
      },
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.Black,
        checkedTrackColor = Color.Black,
      )
    )

    Text(
      text = if (traceMode is TraceMode.Live) {
        "Live Mode"
      } else {
        "File Mode"
      },
      fontSize = 12.sp,
      fontStyle = FontStyle.Italic
    )
  }
}
