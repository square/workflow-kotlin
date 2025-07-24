package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.squareup.workflow1.traceviewer.ui.RightInfoPanel
import com.squareup.workflow1.traceviewer.ui.TraceModeToggleSwitch
import com.squareup.workflow1.traceviewer.util.RenderTrace
import com.squareup.workflow1.traceviewer.util.SandboxBackground
import com.squareup.workflow1.traceviewer.util.UploadFile
import io.github.vinceglb.filekit.PlatformFile

/**
 * Main composable that provides the different layers of UI.
 */
@Composable
internal fun App(
  modifier: Modifier = Modifier
) {
  var selectedNode by remember { mutableStateOf<NodeUpdate?>(null) }
  val workflowFrames = remember { mutableStateListOf<Node>() }
  var frameIndex by remember { mutableIntStateOf(0) }
  val sandboxState = remember { SandboxState() }

  // Default to File mode, and can be toggled to be in Live mode.
  var traceMode by remember { mutableStateOf<TraceMode>(TraceMode.File(null)) }
  var selectedTraceFile by remember { mutableStateOf<PlatformFile?>(null) }

  LaunchedEffect(sandboxState) {
    snapshotFlow { frameIndex }.collect {
      sandboxState.reset()
    }
  }

  Box(
    modifier = modifier
  ) {
    fun resetStates() {
      selectedTraceFile = null
      selectedNode = null
      frameIndex = 0
      workflowFrames.clear()
    }

    // Main content
    SandboxBackground(
      sandboxState = sandboxState,
    ) {
      // if there is not a file selected and trace mode is live, then don't render anything.
      val readyForFileTrace = traceMode is TraceMode.File && selectedTraceFile != null
      val readyForLiveTrace = traceMode is TraceMode.Live
      if (readyForFileTrace || readyForLiveTrace) {
        RenderTrace(
          traceSource = traceMode,
          frameInd = frameIndex,
          onFileParse = { workflowFrames.addAll(it) },
          onNodeSelect = { node, prevNode ->
            selectedNode = NodeUpdate(node, prevNode)
          },
          onNewFrame = { frameIndex += 1 }
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

    TraceModeToggleSwitch(
      onToggle = {
        resetStates()
        traceMode = if (traceMode is TraceMode.Live) {
          frameIndex = 0
          TraceMode.File(null)
        } else {
          // TODO: TraceRecorder needs to be able to take in multiple clients if this is the case
          /*
          We set the frame to -1 here since we always increment it during Live mode as the list of
          frames get populated, so we avoid off by one when indexing into the frames.
           */
          frameIndex = -1
          TraceMode.Live
        }
      },
      traceMode = traceMode,
      modifier = Modifier.align(Alignment.BottomCenter)
    )

    // The states are reset when a new file is selected.
    if (traceMode is TraceMode.File) {
      UploadFile(
        resetOnFileSelect = {
          resetStates()
          selectedTraceFile = it
          traceMode = TraceMode.File(it)
        },
        modifier = Modifier.align(Alignment.BottomStart)
      )
    }
  }
}

internal class SandboxState {
  var offset by mutableStateOf(Offset.Zero)
  var scale by mutableFloatStateOf(1f)

  fun reset() {
    offset = Offset.Zero
  }
}

internal sealed interface TraceMode {
  data class File(val file: PlatformFile?) : TraceMode
  data object Live : TraceMode
}
