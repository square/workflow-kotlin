package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
public fun RenderDiagram(
  traceFile: PlatformFile,
  frameInd: Int,
  onFileParse: (List<Node>) -> Unit,
  onNodeSelect: (Node) -> Unit,
  modifier: Modifier = Modifier
) {
  var frames by remember { mutableStateOf<List<Node>>(emptyList()) }
  var isLoading by remember(traceFile) { mutableStateOf(true) }
  var error by remember(traceFile) { mutableStateOf<Throwable?>(null) }

  LaunchedEffect(traceFile) {
    val parseResult = parseTrace(traceFile)

    when (parseResult) {
      is ParseResult.Failure -> {
        error = parseResult.error
      }
      is ParseResult.Success -> {
        val parsedFrames = parseResult.trace ?: emptyList()
        frames = parsedFrames
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
    DrawTree(frames[frameInd], onNodeSelect)
  }

  // TODO: catch errors and display UI here
}

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 */
@Composable
private fun DrawTree(
  node: Node,
  onNodeSelect: (Node) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(5.dp)
      .border(1.dp, Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    DrawNode(node, onNodeSelect)

    // Draws the node's children recursively.
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Top
    ) {
      node.children.forEach { childNode ->
        DrawTree(childNode, onNodeSelect)
      }
    }
  }
}

/**
 * A basic box that represents a workflow node
 */
@Composable
private fun DrawNode(
  node: Node,
  onNodeSelect: (Node) -> Unit,
) {
  Box(
    modifier = Modifier
      .clickable {
        // Selecting a node will bubble back up to the main view to handle the selection
        onNodeSelect(node)
      }
      .padding(10.dp)
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = node.name)
      Text(text = "ID: ${node.id}")
    }
  }
}
