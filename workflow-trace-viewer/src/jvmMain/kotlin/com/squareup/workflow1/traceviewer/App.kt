package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
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
