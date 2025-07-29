package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate
import com.squareup.workflow1.traceviewer.ui.ColorLegend
import com.squareup.workflow1.traceviewer.ui.DisplayDevices
import com.squareup.workflow1.traceviewer.ui.FrameSelectTab
import com.squareup.workflow1.traceviewer.ui.RightInfoPanel
import com.squareup.workflow1.traceviewer.ui.SearchBox
import com.squareup.workflow1.traceviewer.ui.TraceModeToggleSwitch
import com.squareup.workflow1.traceviewer.util.FileDump
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
  var frameSize by remember { mutableIntStateOf(0) }
  var rawRenderPass by remember { mutableStateOf("") }
  var frameIndex by remember { mutableIntStateOf(0) }
  val sandboxState = remember { SandboxState() }
  val nodeLocations = remember { mutableListOf<SnapshotStateMap<Node, Offset>>() }

  // Default to File mode, and can be toggled to be in Live mode.
  var active by remember { mutableStateOf(false) }
  var traceMode by remember { mutableStateOf<TraceMode>(TraceMode.File(null)) }
  var selectedTraceFile by remember { mutableStateOf<PlatformFile?>(null) }
  // frameIndex is set to -1 when app is in Live Mode, so we increment it by one to avoid off-by-one errors
  val frameInd = if (traceMode is TraceMode.Live) frameIndex + 1 else frameIndex

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
      frameSize = 0
      active = false
      nodeLocations.clear()
    }

    // Main content
    SandboxBackground(
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

    Column(
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      if (active) {
        FrameSelectTab(
          size = frameSize,
          currentIndex = frameIndex,
          onIndexChange = { frameIndex = it },
        )

        // Since we can jump from frame to frame, we fill in the map during each recomposition
        if (nodeLocations.getOrNull(frameInd) == null) {
          // frameSize has not been updated yet, so on the first frame, frameSize = nodeLocations.size = 0,
          // and it will append a new map
          while (nodeLocations.size <= frameSize) {
            nodeLocations.add(mutableStateMapOf())
          }
        }

        SearchBox(
          nodes = nodeLocations[frameInd].keys.toList(),
          onSearch = { name ->
            val node = nodeLocations[frameInd].keys.firstOrNull { it.name == name }
            sandboxState.offset = nodeLocations[frameInd][node] ?: sandboxState.offset
          },
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
      }
    }

    TraceModeToggleSwitch(
      onToggle = {
        resetStates()
        traceMode = if (traceMode is TraceMode.Live) {
          frameIndex = 0
          TraceMode.File(null)
        } else {
          /*
          We set the frame to -1 here since we always increment it during Live mode as the list of
          frames get populated, so we avoid off by one when indexing into the frames.
           */
          frameIndex = -1
          TraceMode.Live()
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

    if (traceMode is TraceMode.Live) {
      if ((traceMode as TraceMode.Live).device == null) {
        DisplayDevices(
          onDeviceSelect = { selectedDevice ->
            traceMode = TraceMode.Live(selectedDevice)
          },
          devices = listDevices(),
          modifier = Modifier.align(Alignment.Center)
        )
      }

      FileDump(
        trace = rawRenderPass,
        modifier = Modifier.align(Alignment.BottomStart)
      )
    }

    if (active) {
      ColorLegend(
        modifier = Modifier.align(Alignment.BottomEnd)
      )
    }

    RightInfoPanel(
      selectedNode = selectedNode,
      modifier = Modifier
        .align(Alignment.TopEnd)
    )
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

/**
 * Allows users to select from multiple devices that are currently running.
 */
internal fun listDevices(): List<String> {
  val process = ProcessBuilder("adb", "devices", "-l").start()
  process.waitFor()
  // We drop the header "List of devices attached"
  return process.inputStream.bufferedReader().readLines().drop(1).dropLast(1)
}
