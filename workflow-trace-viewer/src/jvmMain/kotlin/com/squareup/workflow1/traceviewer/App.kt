package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import com.squareup.workflow1.traceviewer.ui.InfoPanel
import com.squareup.workflow1.traceviewer.utils.LoadWorkflowContent
import com.squareup.workflow1.traceviewer.utils.SandboxBackground
import com.squareup.workflow1.traceviewer.utils.UploadFile
import io.github.vinceglb.filekit.PlatformFile

/**
 * Main composable that provides the different layers of UI.
 */
@Composable
public fun App(
  modifier: Modifier = Modifier
) {
  var selectedFile by remember { mutableStateOf<PlatformFile?>(null) }
  var selectedNode by remember { mutableStateOf<WorkflowNode?>(null) }
  // Used when user selects a new file in [UploadFile]
  val resetSelectedNode = { selectedNode = null }

  Box {
    // Main content
    if (selectedFile != null) {
      SandboxBackground {
        LoadWorkflowContent(selectedFile) {
          selectedNode = it
        }
      }
    }

    // Left side information panel
    InfoPanel(
      selectedNode
    )

    // Bottom right upload button
    UploadFile(resetSelectedNode, { selectedFile = it })
  }
}
