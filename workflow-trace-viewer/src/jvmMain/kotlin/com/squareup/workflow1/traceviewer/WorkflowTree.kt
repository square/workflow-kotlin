package com.squareup.workflow1.traceviewer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * Since the logic of Workflow is hierarchical (where each workflow may have parent workflows and/or children workflows,
 * a tree structure is most appropriate for representing the data rather than using flat data structures like an array.
 *
 * TBD what more metadata should be involved with each node, e.g. (props, states, # of render passes)
 */
public data class WorkflowNode (
  val id: String,
  val name: String,
  val children: List<WorkflowNode>
) {
  // fun findParentForId(id: String): WorkflowNode? {
  //   if (this.id == id) {
  //     return null // This is the root node, so it has no parent
  //   }
  //   if (children.any { it.id == id }) {
  //     return this
  //   }
  //   return children.firstNotNullOfOrNull {
  //     it.findParentForId(id)
  //   }
  // }
}

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 */
@Composable
public fun DrawWorkflowTree(
  node: WorkflowNode,
) {
  Column(
    modifier = Modifier
      .padding(5.dp)
      .border(1.dp,Color.Black)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // draws itself
    drawNode(node)

    // draws children recursively
    Row (
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Top
    ) {
      node.children.forEach { childNode ->
        DrawWorkflowTree (childNode)
      }
    }
  }
}

/**
 * Basic data, for now.
 * These can be designed to be clickable and be expanded to show more information.
 */
@Composable
private fun drawNode(
  node: WorkflowNode,
) {
  val open = remember { mutableStateOf(false) }
  Box (
    modifier = Modifier
      .clickable { open.value = !open.value }
      .padding(10.dp)
  ){
    Column (horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = node.name)
      Text(text = "ID: ${node.id}")
      if (open.value) {
        Text("node is opened")
      }
    }
  }
}
