package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate
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
  var selectedNode by remember { mutableStateOf<NodeUpdate?>(null) }
  var workflowFrames by remember { mutableStateOf<List<Node>>(emptyList()) }
  var frameIndex by remember { mutableIntStateOf(0) }
  val sandboxState = remember { SandboxState() }

  LaunchedEffect(sandboxState) {
    snapshotFlow { frameIndex }.collect {
      sandboxState.reset()
    }
  }

  Box(
    modifier = modifier
  ) {
    // Main content
    if (selectedTraceFile != null) {
      SandboxBackground(
        sandboxState = sandboxState,
      ) {
        RenderDiagram(
          traceFile = selectedTraceFile!!,
          frameInd = frameIndex,
          onFileParse = { workflowFrames = it },
          onNodeSelect = { node, prevNode ->
            selectedNode = NodeUpdate(node, prevNode)
          }
        )
      }
    }

    FrameSelectTab(
      frames = workflowFrames,
      currentIndex = frameIndex,
      onIndexChange = { frameIndex = it },
      modifier = Modifier.align(Alignment.TopCenter)
    )

    RightInfoPanel(
      selectedNode = selectedNode,
      modifier = Modifier
        .align(Alignment.TopEnd)
    )

    // The states are reset when a new file is selected.
    UploadFile(
      resetOnFileSelect = {
        selectedTraceFile = it
        selectedNode = null
        frameIndex = 0
        workflowFrames = emptyList()
      },
      modifier = Modifier.align(Alignment.BottomStart)
    )
  }
}

internal class SandboxState {
  var offset by mutableStateOf(Offset.Zero)
  var scale by mutableFloatStateOf(1f)

  fun reset() {
    offset = Offset.Zero
    scale = 1f
  }
}
