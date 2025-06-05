package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

@Composable
public fun UploadFile(onFileSelected: (PlatformFile?) -> Unit) {
  Box (modifier = Modifier
    .padding(16.dp)
    .fillMaxSize()
  ){
    val launcher = rememberFilePickerLauncher(
      type = FileKitType.File(listOf("json","txt")),
      title = "Select Workflow Trace File"
    ) {
      onFileSelected(it)
    }
    Button(
      onClick = { launcher.launch() },
      modifier = Modifier
        .align(Alignment.BottomEnd),
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
}
