package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.squareup.moshi.JsonAdapter
import com.squareup.workflow1.traceviewer.TraceMode
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.util.ParseResult
import com.squareup.workflow1.traceviewer.util.createMoshiAdapter
import com.squareup.workflow1.traceviewer.util.parseFileTrace
import com.squareup.workflow1.traceviewer.util.parseLiveTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Access point for drawing the main content of the app. It will load the trace for given files and
 * tabs. This will also all errors related to errors parsing a given trace JSON file.
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
  var error by remember(traceSource) { mutableStateOf<Throwable?>(null) }

  val frames = remember(traceSource) { mutableStateListOf<Node>() }
  val fullTree = remember(traceSource) { mutableStateListOf<Node>() }
  val affectedNodes = remember(traceSource) { mutableStateListOf<Set<Node>>() }

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
  ): Boolean {
    return when (parseResult) {
      is ParseResult.Failure -> {
        error = parseResult.error
        false
      }
      is ParseResult.Success -> {
        addToStates(
          frame = parseResult.trace,
          tree = parseResult.trees,
          affected = parseResult.affectedNodes
        )
        onNewFrame?.invoke()
        true
      }
    }
  }

  LaunchedEffect(traceSource) {
    when (traceSource) {
      is TraceMode.File -> {
        // We guarantee the file is null since this composable can only be called when a file is selected.
        val parseResult = parseFileTrace(traceSource.file!!)
        handleParseResult(parseResult)
      }

      is TraceMode.Live -> {
        val socket = traceSource.socket
        socket.beginListen(this)
        val adapter: JsonAdapter<List<Node>> = createMoshiAdapter<Node>()

        withContext(Dispatchers.IO) {
          // Since channel implements ChannelIterator, we can for-loop through on the receiver end.
          for (renderPass in socket.renderPassChannel) {
            val currentTree = if (fullTree.isEmpty()) null else fullTree.last()
            val parseResult = parseLiveTrace(renderPass, adapter, currentTree)
            handleParseResult(parseResult, onNewFrame)
          }
        }
      }
    }
  }

  if (error != null) {
    Text("Error parsing: ${error?.message}")
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

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 *
 * A mutable map is used to persist the expansion state of the nodes, allowing them to be open and
 * closed from user clicks.
 */
@Composable
private fun DrawTree(
  node: Node,
  previousNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (Node, Node?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(5.dp)
      .border(1.dp, Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val isAffected = affectedNodes.contains(node)
    // By default, nodes that relevant to this specific frame are expanded. All others are closed.
    LaunchedEffect(expandedNodes) {
      expandedNodes[node.id] = isAffected
    }
    val isExpanded = expandedNodes[node.id] == true

    DrawNode(
      node,
      previousNode,
      isAffected,
      isExpanded,
      onNodeSelect,
      onExpandToggle = { expandedNodes[node.id] = !expandedNodes[node.id]!! }
    )

    // Draws the node's children recursively.
    if (isExpanded) {
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
      ) {
        /*
        We pair up the current node's children with previous frame's children.
        In the edge case that the current frame has additional children compared to the previous
        frame, we replace with null and will check before next recursive call.
         */
        node.children.forEach { (index, childNode) ->
          val prevChildNode = previousNode?.children?.get(index)
          DrawTree(
            node = childNode,
            previousNode = prevChildNode,
            affectedNodes = affectedNodes,
            expandedNodes = expandedNodes,
            onNodeSelect = onNodeSelect
          )
        }
      }
    }
  }
}

/**
 * A basic box that represents a workflow node
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DrawNode(
  node: Node,
  previousNode: Node?,
  isAffected: Boolean,
  isExpanded: Boolean,
  onNodeSelect: (Node, Node?) -> Unit,
  onExpandToggle: (Node) -> Unit,
) {
  Box(
    modifier = Modifier
      .background(if (isAffected) Color.Green else Color.Transparent)
      .onPointerEvent(PointerEventType.Press) {
        if (it.buttons.isPrimaryPressed) {
          onNodeSelect(node, previousNode)
        } else if (it.buttons.isSecondaryPressed) {
          onExpandToggle(node)
        }
      }
      .padding(10.dp)
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (node.children.isNotEmpty()) {
          Text(text = if (isExpanded) "▼" else "▶")
        }
        Text(text = node.name)
      }
      Text(text = "ID: ${node.id}")
    }
  }
}
