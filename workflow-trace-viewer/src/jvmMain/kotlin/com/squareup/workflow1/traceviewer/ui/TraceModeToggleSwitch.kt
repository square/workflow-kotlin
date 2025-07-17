package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
  modifier: Modifier
) {
  // File mode is unchecked by default, and live mode is checked.
  var checked by remember {
    mutableStateOf(traceMode is TraceMode.Live)
  }

  Column(
    modifier = modifier.padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Switch(
      checked = checked,
      onCheckedChange = {
        checked = it
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
