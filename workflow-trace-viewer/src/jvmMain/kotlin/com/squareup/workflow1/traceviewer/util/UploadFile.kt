package com.squareup.workflow1.traceviewer.util

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

/**
 * Provides functionality for user to upload a JSON or .txt file from their local devices, which
 * contains information pulled from workflow traces
 */
@Composable
public fun UploadFile(
  onFileSelect: (PlatformFile?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val launcher = rememberFilePickerLauncher(
    type = FileKitType.File(listOf("json", "txt")),
    title = "Select Workflow Trace File"
  ) {
    onFileSelect(it)
  }

  Button(
    onClick = { launcher.launch() },
    modifier = modifier.padding(16.dp),
    shape = CircleShape,
    colors = buttonColors(Color.Black)
  ) {
    Text(
      text = "+",
      color = Color.White,
      fontSize = 24.sp,
      fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    )
  }
}
