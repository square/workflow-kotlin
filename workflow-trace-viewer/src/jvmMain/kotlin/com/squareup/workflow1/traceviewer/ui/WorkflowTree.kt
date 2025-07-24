package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.squareup.moshi.JsonAdapter
import com.squareup.workflow1.traceviewer.TraceMode
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate
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
  onFileParse: (Int) -> Unit,
  onNodeSelect: (NodeUpdate) -> Unit,
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
    onFileParse(frame.size)
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
      previousFrameNode = previousFrame,
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DrawTree(
  node: Node,
  previousFrameNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (NodeUpdate) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(6.dp)
      .border(3.dp, Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val groupKey = "${node.id}-unaffected"
    val isAffected = affectedNodes.contains(node)
    // By default, nodes that relevant to this specific frame are expanded. All others are closed.
    LaunchedEffect(expandedNodes) {
      expandedNodes[node.id] = isAffected
      expandedNodes[groupKey] = false
    }
    val isExpanded = expandedNodes[node.id] == true
    val unaffectedChildrenExpanded = expandedNodes[groupKey] == true

    DrawNode(
      node = node,
      nodePast = previousFrameNode,
      isAffected = isAffected,
      isExpanded = isExpanded,
      onNodeSelect = onNodeSelect,
      onExpandToggle = { expandedNodes[node.id] = !expandedNodes[node.id]!! }
    )

    // Draws the node's children recursively.
    if (isExpanded) {
      // Draw the affected children, and only draw the unaffected children it is clicked annd expanded.
      val (affectedChildren, unaffectedChildren) = node.children.values
        .partition { affectedNodes.contains(it) }


      if (unaffectedChildren.isNotEmpty()) {
        Box (
          modifier = Modifier
            .onPointerEvent(PointerEventType.Press) {
            if (it.buttons.isSecondaryPressed) {
              // The open/close state for this group is always set when this node is first composed.
              expandedNodes[groupKey] = !expandedNodes[groupKey]!!
            }
          }
        ) {
          if (!unaffectedChildrenExpanded) {
            Column(
              modifier = Modifier
                .background(Color.LightGray.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                .border(1.dp, Color.Gray)
                .padding(8.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(text = "${node.name}'s", color = Color.DarkGray)
              Text(text = "${unaffectedChildren.size} unaffected children", color = Color.DarkGray, fontSize = 12.sp)
            }
          } else {
            DrawChildren(
              children = unaffectedChildren,
              previousFrameNode = previousFrameNode,
              affectedNodes = affectedNodes,
              expandedNodes = expandedNodes,
              onNodeSelect = onNodeSelect
            )
          }
        }
      }

      if (affectedChildren.isNotEmpty()) {
        DrawChildren(
          children = affectedChildren,
          previousFrameNode = previousFrameNode,
          affectedNodes = affectedNodes,
          expandedNodes = expandedNodes,
          onNodeSelect = onNodeSelect
        )
      }
    }
  }
}

@Composable
private fun DrawChildren(
  children: List<Node>,
  previousFrameNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (NodeUpdate) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.Top
  ) {
    children.forEach { childNode ->
      DrawTree(
        node = childNode,
        previousFrameNode = previousFrameNode?.children?.get(childNode.id),
        affectedNodes = affectedNodes,
        expandedNodes = expandedNodes,
        onNodeSelect = onNodeSelect
      )
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
  nodePast: Node?,
  isAffected: Boolean,
  isExpanded: Boolean,
  onNodeSelect: (NodeUpdate) -> Unit,
  onExpandToggle: (Node) -> Unit,
) {
  val nodeUpdate = NodeUpdate.create(
    current = node,
    past = nodePast,
    isAffected = isAffected
  )

  Box(
    modifier = Modifier
      .background(nodeUpdate.state.color)
      .onPointerEvent(PointerEventType.Press) {
        if (it.buttons.isPrimaryPressed) {
          onNodeSelect(nodeUpdate)
        } else if (it.buttons.isSecondaryPressed) {
          onExpandToggle(node)
        }
      }
      .padding(16.dp)
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
