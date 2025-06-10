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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Since the logic of Workflow is hierarchical (where each workflow may have parent workflows and/or
 * children workflows, a tree structure is most appropriate for representing the data rather than
 * using flat data structures like an array.
 *
 * TBD what more metadata should be involved with each node, e.g. (props, states, # of render passes)
 */
public data class WorkflowNode(
  val id: String,
  val name: String,
  val children: List<WorkflowNode>
)

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree.
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node.
 */
@Composable
public fun DrawWorkflowTree(
  node: WorkflowNode,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(5.dp)
      .border(1.dp, Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // draws itself
    DrawNode(node)

    // draws children recursively
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Top
    ) {
      node.children.forEach { childNode ->
        DrawWorkflowTree(childNode)
      }
    }
  }
}

/**
 * A basic box that represents a workflow node.
 */
@Composable
private fun DrawNode(
  node: WorkflowNode,
) {
  val open = remember { mutableStateOf(false) }
  Box(
    modifier = Modifier
      .clickable { open.value = !open.value }
      .padding(10.dp)
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = node.name)
      Text(text = "ID: ${node.id}")
      if (open.value) {
        Text("node is opened")
      }
    }
  }
}
