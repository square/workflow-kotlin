package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import com.squareup.workflow1.traceviewer.ui.InfoPanel
import com.squareup.workflow1.traceviewer.ui.RenderDiagram
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
  var snapshotIndex by remember { mutableIntStateOf(0) }

  // Used when user selects a new file in [UploadFile]
  val resetSelectedNode = { selectedNode = null }

  Box {
    // Main content
    if (selectedFile != null) {
      SandboxBackground {
        RenderDiagram(
          file = selectedFile,
          traceInd = snapshotIndex,
          onNodeSelect = { selectedNode = it }
        )
      }
    }

    // Top trace selector row
    // TraceSelectRow { snapshotIndex.value = it }

    // Left side information panel
    InfoPanel(selectedNode)

    // Bottom right upload button
    UploadFile(resetSelectedNode, { selectedFile = it })
  }
}
