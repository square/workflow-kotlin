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



@Composable
private fun InfoPanel(
  selectedNode: WorkflowNode?
) {
  Row {
    val panelOpen = remember { mutableStateOf(false) }

    // based on open/close, display the node details (Column)
    if (panelOpen.value) {
      PanelDetails(
        selectedNode,
        Modifier.fillMaxWidth(.35f)
      )
    }

    IconButton(
      onClick = { panelOpen.value = !panelOpen.value },
      modifier = Modifier
        .padding(8.dp)
        .size(30.dp)
        .align(Alignment.Top)
    ) {
      Icon(
        imageVector = if (panelOpen.value) Filled.KeyboardArrowLeft else Filled.KeyboardArrowRight,
        contentDescription = if (panelOpen.value) "Close Panel" else "Open Panel",
        modifier = Modifier
      )
    }
  }
}

@Composable
private fun PanelDetails(
  node: WorkflowNode?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier
      .fillMaxHeight()
      .background(Color.LightGray)
  ) {
    if (node == null) {
      Text("No node selected")
      return@Column
    }

    Column(
      modifier = Modifier
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text("only visible with a node selected")
      Text(
        text = "This is a node panel for ${node.name}",
        fontSize = 20.sp,
        modifier = Modifier.padding(8.dp)
      )
    }
  }
}
