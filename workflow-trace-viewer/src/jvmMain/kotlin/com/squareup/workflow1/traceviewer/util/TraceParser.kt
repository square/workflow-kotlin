package com.squareup.workflow1.traceviewer.util

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
import com.squareup.moshi.JsonAdapter
import com.squareup.workflow1.traceviewer.TraceMode
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.ui.DrawTree

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
  onFileParse: (List<Node>) -> Unit,
  onNodeSelect: (Node, Node?) -> Unit,
  onNewFrame: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isLoading by remember(traceSource) { mutableStateOf(true) }
  var error by remember(traceSource) { mutableStateOf<String?>(null) }
  val frames = remember { mutableStateListOf<Node>() }
  val fullTree = remember { mutableStateListOf<Node>() }
  val affectedNodes = remember { mutableStateListOf<Set<Node>>() }

  // Updates current state with the new data from trace source.
  fun addToStates(frame: List<Node>, tree: List<Node>, affected: List<Set<Node>>) {
    frames.addAll(frame)
    fullTree.addAll(tree)
    affectedNodes.addAll(affected)
    isLoading = false
    onFileParse(frame)
  }

  // Handles the result of parsing a trace, either from file or live. Live mode includes callback
  // for when a new frame is received.
  fun handleParseResult(
    parseResult: ParseResult,
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
        onNewFrame?.invoke()
      }
    }
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
        val adapter: JsonAdapter<List<Node>> = createMoshiAdapter<Node>()
        streamRenderPassesFromDevice { renderPass ->
          val currentTree = fullTree.lastOrNull()
          val parseResult = parseLiveTrace(renderPass, adapter, currentTree)
          handleParseResult(parseResult, onNewFrame)
        }
        error = "Socket has already been closed or is not available."
      }
    }
  }

  if (error != null) {
    Text("Error parsing: ${error}")
    return
  }

  if (!isLoading) {
    val previousFrame = if (frameInd > 0) fullTree[frameInd - 1] else null
    DrawTree(
      node = fullTree[frameInd],
      previousNode = previousFrame,
      affectedNodes = affectedNodes[frameInd],
      expandedNodes = remember(frameInd) { mutableStateMapOf() },
      onNodeSelect = onNodeSelect,
    )
  }
}
