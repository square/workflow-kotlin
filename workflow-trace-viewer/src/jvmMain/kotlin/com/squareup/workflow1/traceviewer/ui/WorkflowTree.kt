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
import com.squareup.workflow1.traceviewer.model.WorkflowNode
import com.squareup.workflow1.traceviewer.utils.fetchTrace
import io.github.vinceglb.filekit.PlatformFile

/**
 * Access point for drawing the main content of the app. It will load the trace for given files and
 * tabs. This will also all errors related to errors parsing a given trace JSON file.
 */
@Composable
public fun RenderDiagram(
  file: PlatformFile,
  traceInd: Int,
  onFileParse: (List<WorkflowNode>) -> Unit,
  onNodeSelect: (WorkflowNode) -> Unit,
) {
  var workflowNodes by remember { mutableStateOf<List<WorkflowNode>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(file) {
    workflowNodes = fetchTrace(file)
    onFileParse(workflowNodes)
    isLoading = false
  }

  if (!isLoading) {
    DrawTree(workflowNodes[traceInd], onNodeSelect)
  }

  // TODO: catch errors and display UI here
}

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 */
@Composable
private fun DrawTree(
  node: WorkflowNode,
  onNodeSelect: (WorkflowNode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(5.dp)
      .border(1.dp, Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // draws the node itself
    DrawNode(node, onNodeSelect)

    // draws the node's children recursively
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
  node: WorkflowNode,
  onNodeSelect: (WorkflowNode) -> Unit,
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
