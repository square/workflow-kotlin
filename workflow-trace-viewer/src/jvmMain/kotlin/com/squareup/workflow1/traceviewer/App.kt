package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.ui.FrameSelectTab
import com.squareup.workflow1.traceviewer.ui.RenderDiagram
import com.squareup.workflow1.traceviewer.ui.RightInfoPanel
import com.squareup.workflow1.traceviewer.util.SandboxBackground
import com.squareup.workflow1.traceviewer.util.UploadFile
import io.github.vinceglb.filekit.PlatformFile

/**
 * Main composable that provides the different layers of UI.
 */
@Composable
public fun App(
  modifier: Modifier = Modifier
) {
  var selectedTraceFile by remember { mutableStateOf<PlatformFile?>(null) }
  var selectedNode by remember { mutableStateOf<Node?>(null) }
  var workflowFrames by remember { mutableStateOf<List<Node>>(emptyList()) }
  var frameIndex by remember { mutableIntStateOf(0) }

  Box(
    modifier = modifier
  ) {
    // Main content
    if (selectedTraceFile != null) {
      SandboxBackground {
        RenderDiagram(
          traceFile = selectedTraceFile!!,
          frameInd = frameIndex,
          onFileParse = { workflowFrames = it },
          onNodeSelect = { selectedNode = it }
        )
      }
    }

    FrameSelectTab(
      frames = workflowFrames,
      currentIndex = frameIndex,
      onIndexChange = { frameIndex = it },
      modifier = Modifier.align(Alignment.TopCenter)
    )

    RightInfoPanel(selectedNode)

    // The states are reset when a new file is selected.
    UploadFile(
      onFileSelect = {
        selectedTraceFile = it
        selectedNode = null
        frameIndex = 0
      },
      modifier = Modifier.align(Alignment.BottomStart)
    )
  }
}
