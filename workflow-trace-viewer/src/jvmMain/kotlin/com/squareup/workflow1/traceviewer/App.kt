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
import io.github.vinceglb.filekit.readString


@Composable
public fun App() {
  Box {
    val selectedFile = remember { mutableStateOf<PlatformFile?>(null)}

    if (selectedFile.value != null){
      SandboxBackground { WorkflowContent(selectedFile.value) }
    }

    UploadFile{ selectedFile.value = it}
  }
}

@Composable
private fun WorkflowContent(file: PlatformFile?) {
  val jsonString = remember { mutableStateOf<String?>(null) }
  LaunchedEffect(file){
    jsonString.value = file?.readString()
  }
  val root = jsonString.value?.let { FetchRoot(it) }

  if (root != null) {
    DrawWorkflowTree(root)
  } else {
    Text("Empty data or failed to parse data") // TODO: proper handling of error
  }
}
