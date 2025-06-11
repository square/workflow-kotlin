package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString

/**
 * Main composable that provides the different layers of UI.
 */
@Composable
public fun App(
  modifier: Modifier = Modifier
) {
  Box {
    var selectedFile by remember { mutableStateOf<PlatformFile?>(null) }

    if (selectedFile != null) {
      SandboxBackground { WorkflowContent(selectedFile!!) }
    }

    UploadFile(onFileSelect = { selectedFile = it })
  }
}

@Composable
private fun WorkflowContent(file: PlatformFile) {
  var jsonString by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(file) {
    jsonString = file.readString()
  }
  val root = jsonString?.let { parseTrace(it) }

  if (root != null) {
    DrawWorkflowTree(root)
  } else {
    Text("Empty data or failed to parse data") // TODO: proper handling of error
  }
}
