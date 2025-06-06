package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import com.squareup.workflow1.traceviewer.ui.InfoPanel
import com.squareup.workflow1.traceviewer.utils.LoadWorkflowContent
import com.squareup.workflow1.traceviewer.utils.SandboxBackground
import com.squareup.workflow1.traceviewer.utils.UploadFile
import io.github.vinceglb.filekit.PlatformFile

@Composable
public fun App(
  modifier: Modifier = Modifier,
) {
  val selectedFile = remember { mutableStateOf<PlatformFile?>(null) }
  val selectedNode = remember { mutableStateOf<WorkflowNode?>(null) }

  // Used when user selects a new file in [UploadFile]
  val resetSelectedNode = { selectedNode.value = null }

  Box {
    // Main content
    if (selectedFile.value != null) {
      SandboxBackground {
        LoadWorkflowContent(selectedFile.value) {
          selectedNode.value = it
        }
      }
    }

    // Left side information panel
    InfoPanel(
      selectedNode.value
    )

    // Bottom right upload button
    UploadFile(resetSelectedNode, { selectedFile.value = it })
  }
}
