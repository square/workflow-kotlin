package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun DisplayDevices(
  onDeviceSelected: (String) -> Unit,
  devices: List<String>,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    if (devices.isEmpty()) {
      Text(
        text = "No device available",
        modifier = Modifier.align(Alignment.Center)
      )
      return@Box
    }

    val emulatorRegex = Regex("""\bemulator-\d+\b""")
    Column {
      devices.forEach { device ->
        Card(
          onClick = {
            emulatorRegex.find(device)?.value?.let { emulator ->
              onDeviceSelected(emulator)
            }
          },
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, Color.Gray),
          modifier = Modifier.padding(4.dp),
          elevation = 2.dp
        ) {
          Text(
            text = device,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
      }
    }
  }
}
