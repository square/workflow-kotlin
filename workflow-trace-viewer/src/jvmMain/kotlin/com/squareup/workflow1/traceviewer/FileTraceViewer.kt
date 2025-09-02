package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate
import com.squareup.workflow1.traceviewer.ui.RightInfoPanel
import com.squareup.workflow1.traceviewer.ui.control.FileDump
import com.squareup.workflow1.traceviewer.ui.control.FrameNavigator
import com.squareup.workflow1.traceviewer.ui.control.SearchBox
import com.squareup.workflow1.traceviewer.util.SandboxBackground
import com.squareup.workflow1.traceviewer.util.parser.RenderTrace
import io.github.vinceglb.filekit.PlatformFile

/**
 * Main composable that provides the different layers of UI.
 */
@Composable
internal fun TraceViewerWindow(
  modifier: Modifier = Modifier,
  traceMode: TraceMode,
) {
  var appWindowSize by remember { mutableStateOf(IntSize(0, 0)) }
  var selectedNode by remember { mutableStateOf<NodeUpdate?>(null) }
  var frameSize by remember { mutableIntStateOf(0) }
  var rawRenderPass by remember { mutableStateOf("") }
  var frameIndex by remember { mutableIntStateOf(if (traceMode is TraceMode.Live) -1 else 0) }
  val sandboxState = remember { SandboxState() }
  val nodeLocations = remember { mutableStateListOf<SnapshotStateMap<Node, Offset>>() }

  // Default to File mode, and can be toggled to be in Live mode.
  var active by remember { mutableStateOf(false) }
  // frameIndex is set to -1 when app is in Live Mode, so we increment it by one to avoid off-by-one errors
  val frameInd = if (traceMode is TraceMode.Live) frameIndex + 1 else frameIndex

  LaunchedEffect(sandboxState) {
    snapshotFlow { frameIndex }.collect {
      sandboxState.reset()
    }
  }

  Box(
    modifier = modifier.onSizeChanged {
      appWindowSize = it
    }
  ) {

    // Main content
    SandboxBackground(
      appWindowSize = appWindowSize,
      sandboxState = sandboxState,
    ) {
      // if there is not a file selected and trace mode is live, then don't render anything.
      val readyForFileTrace = TraceMode.validateFileMode(traceMode)
      val readyForLiveTrace = TraceMode.validateLiveMode(traceMode)

      if (readyForFileTrace || readyForLiveTrace) {
        active = true
        RenderTrace(
          traceSource = traceMode,
          frameInd = frameIndex,
          onFileParse = { frameSize += it },
          onNodeSelect = { selectedNode = it },
          onNewFrame = { frameIndex += 1 },
          onNewData = { rawRenderPass += "$it," },
          storeNodeLocation = { node, loc -> nodeLocations[frameInd] += (node to loc) }
        )
      }
    }

    Row(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      if (active) {
        // Frames that appear in composition may not happen sequentially, so when the current frame
        // locations is null, that means we've skipped frames and need to fill all the intermediate
        // ones. e.g. Frame 1 to Frame 10
        if (nodeLocations.getOrNull(frameInd) == null) {
          // frameSize has not been updated yet, so on the first frame, frameSize = nodeLocations.size = 0,
          // and it will append a new map
          while (nodeLocations.size <= frameSize) {
            nodeLocations += mutableStateMapOf()
          }
        }

        val frameNodeLocations = nodeLocations[frameInd]
        SearchBox(
          nodes = frameNodeLocations.keys.toList(),
          onSearch = { name ->
            val node = frameNodeLocations.keys.first { it.name == name }
            val newX = (sandboxState.offset.x - frameNodeLocations.getValue(node).x
              + appWindowSize.width / 2)
            val newY = (sandboxState.offset.y - frameNodeLocations.getValue(node).y
              + appWindowSize.height / 2)
            sandboxState.offset = Offset(x = newX, y = newY)
          },
        )

        FrameNavigator(
          totalFrames = frameSize,
          currentIndex = frameIndex,
          onIndexChange = { frameIndex = it },
        )
      }
    }
    if (traceMode is TraceMode.Live) {
      FileDump(
        trace = rawRenderPass,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
      )
    }

    RightInfoPanel(
      selectedNode = selectedNode,
      modifier = Modifier.align(Alignment.TopEnd)
    )
  }
}

internal class SandboxState {
  var offset by mutableStateOf(Offset.Zero)

  fun reset() {
    offset = Offset.Zero
  }
}

internal sealed interface TraceMode {
  data class File(val file: PlatformFile?) : TraceMode
  data class Live(val device: String? = null) : TraceMode

  companion object {
    fun validateLiveMode(traceMode: TraceMode): Boolean {
      return traceMode is Live && traceMode.device != null
    }

    fun validateFileMode(traceMode: TraceMode): Boolean {
      return traceMode is File && traceMode.file != null
    }
  }
}
