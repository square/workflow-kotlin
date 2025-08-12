package com.squareup.workflow1.traceviewer.util.parser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.JsonAdapter
import com.squareup.workflow1.traceviewer.TraceMode
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate
import com.squareup.workflow1.traceviewer.ui.DrawTree
import com.squareup.workflow1.traceviewer.util.streamRenderPassesFromDevice

/**
 * Handles parsing the trace's after JsonParser has turned all render passes into frames. Also calls
 * the UI composables to render the full trace.
 *
 * This handles either File or Live trace modes, and will parse equally
 */
@Composable
internal fun RenderTrace(
  traceSource: TraceMode,
  frameInd: Int,
  onFileParse: (Int) -> Unit,
  onNodeSelect: (NodeUpdate) -> Unit,
  onNewFrame: () -> Unit,
  onNewData: (String) -> Unit,
  storeNodeLocation: (Node, Offset) -> Unit,
  modifier: Modifier = Modifier
) {
  var isLoading by remember(traceSource) { mutableStateOf(true) }
  var error by remember(traceSource) { mutableStateOf<String?>(null) }
  val frames = remember(traceSource) { mutableStateListOf<Node>() }
  val fullTree = remember(traceSource) { mutableStateListOf<Node>() }
  val affectedNodes = remember(traceSource) { mutableStateListOf<Set<Node>>() }

  // Updates current state with the new data from trace source.
  fun addToStates(frame: List<Node>, tree: List<Node>, affected: List<Set<Node>>) {
    frames.addAll(frame)
    fullTree.addAll(tree)
    affectedNodes.addAll(affected)
    onFileParse(frame.size)
  }

  // Handles the result of parsing a trace, either from file or live. Live mode includes callback
  // for when a new frame is received.
  fun handleParseResult(
    parseResult: ParseResult,
    rawRenderPass: String? = null,
    onNewFrame: (() -> Unit)? = null
  ) {
    when (parseResult) {
      is ParseResult.Failure -> {
        error = parseResult.error.toString()
      }

      is ParseResult.Success -> {
        addToStates(
          frame = parseResult.trace,
          tree = parseResult.trees,
          affected = parseResult.affectedNodes
        )
        // Only increment the frame index and add the raw data during Live tracing mode.
        onNewFrame?.invoke()
        rawRenderPass?.let { onNewData(it) }
      }
    }
    isLoading = false
  }

  LaunchedEffect(traceSource) {
    when (traceSource) {
      is TraceMode.File -> {
        checkNotNull(traceSource.file) {
          "TraceMode.File should have a non-null file to parse."
        }
        val parseResult = parseFileTrace(traceSource.file)
        handleParseResult(parseResult)
      }

      is TraceMode.Live -> {
        checkNotNull(traceSource.device) {
          "TraceMode.Live requires a selected device"
        }
        val adapter: JsonAdapter<List<Node>> = createMoshiAdapter<Node>()
        streamRenderPassesFromDevice(traceSource.device) { renderPass ->
          val currentTree = fullTree.lastOrNull()
          val parseResult = parseLiveTrace(renderPass, adapter, currentTree)
          handleParseResult(parseResult, renderPass, onNewFrame)
        }
        error = "Socket has already been closed or is not available."
      }
    }
  }

  // This will only happen in the initial switch to Live Mode, where a socket error bubbled up and
  // the lambda call to parse the data was immediately cancelled, meaning handleParseResult was never
  // called to set isLoading to false.
  if (isLoading && error != null) {
    Text("Socket Error: $error")
    return
  }

  // This meant that there was an exception, but it was stored in ParseResult and read in
  // handleParseResult method. Since there is no parsed data, this likely means there was a moshi
  // parsing error.
  if (error != null && frames.isEmpty()) {
    Text("Malformed File: $error")
    return
  }

  if (!isLoading) {
    val previousFrame = if (frameInd > 0) fullTree[frameInd - 1] else null
    DrawTree(
      node = fullTree[frameInd],
      previousFrameNode = previousFrame,
      affectedNodes = affectedNodes[frameInd],
      expandedNodes = remember(frameInd) { mutableStateMapOf() },
      onNodeSelect = onNodeSelect,
      storeNodeLocation = storeNodeLocation
    )

    // This error happens when there has already been previous data parsed, but some exception bubbled
    // up again, meaning it has to be a socket closure in Live mode.
    error?.let {
      Text(
        text = "Socket closed: $error",
        fontSize = 20.sp,
        modifier = modifier.background(Color.White).padding(20.dp)
      )
    }
  }
}
