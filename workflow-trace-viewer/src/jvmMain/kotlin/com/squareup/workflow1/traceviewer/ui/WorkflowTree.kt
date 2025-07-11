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
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.util.ParseResult
import com.squareup.workflow1.traceviewer.util.parseTrace
import io.github.vinceglb.filekit.PlatformFile

/**
 * Access point for drawing the main content of the app. It will load the trace for given files and
 * tabs. This will also all errors related to errors parsing a given trace JSON file.
 */
@Composable
internal fun RenderDiagram(
  traceFile: PlatformFile,
  frameInd: Int,
  onFileParse: (List<Node>) -> Unit,
  onNodeSelect: (Node, Node?) -> Unit,
  modifier: Modifier = Modifier
) {
  var isLoading by remember(traceFile) { mutableStateOf(true) }
  var error by remember(traceFile) { mutableStateOf<Throwable?>(null) }
  var frames by remember { mutableStateOf<List<Node>>(emptyList()) }
  var fullTree by remember { mutableStateOf<List<Node>>(emptyList()) }
  var affectedNodes by remember { mutableStateOf<List<Set<Node>>>(emptyList()) }

  LaunchedEffect(traceFile) {
    val parseResult = parseTrace(traceFile)

    when (parseResult) {
      is ParseResult.Failure -> {
        error = parseResult.error
      }
      is ParseResult.Success -> {
        val parsedFrames = parseResult.trace ?: emptyList()
        frames = parsedFrames
        fullTree = parseResult.trees
        affectedNodes = parseResult.affectedNodes
        onFileParse(parsedFrames)
        isLoading = false
      }
    }
  }

  if (error != null) {
    Text("Error parsing file: ${error?.message}")
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
        node.children.forEachIndexed { index, childNode ->
          val prevChildNode = previousNode?.children?.getOrNull(index)
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
