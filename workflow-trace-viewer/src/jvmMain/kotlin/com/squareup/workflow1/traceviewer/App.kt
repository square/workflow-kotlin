package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import com.squareup.workflow1.traceviewer.ui.InfoPanel
import com.squareup.workflow1.traceviewer.utils.LoadWorkflowContent
import com.squareup.workflow1.traceviewer.utils.SandboxBackground
import com.squareup.workflow1.traceviewer.utils.UploadFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString

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
      selectedNode.value
    )

    // Bottom right upload button
    UploadFile(resetSelectedNode, { selectedFile.value = it })
  }
}
