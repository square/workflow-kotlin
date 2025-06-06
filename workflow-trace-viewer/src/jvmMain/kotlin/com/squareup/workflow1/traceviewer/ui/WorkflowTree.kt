package com.squareup.workflow1.traceviewer

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.WorkflowNode

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 */
@Composable
public fun DrawWorkflowTree(
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
        DrawWorkflowTree(childNode, onNodeSelect)
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
  var open by remember { mutableStateOf(false) }
  Box(
    modifier = Modifier
      .clickable {
        // open.value = !open.value

        // selection will bubble back up to the main view to handle the selection
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
