package com.squareup.workflow1.traceviewer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import javax.swing.tree.TreeNode

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
  // fun findParentById(id: String): WorkflowNode? {
  //   if (this.id == id) return this
  //   for (child in children) {
  //     val found = child.findParentById(id)
  //     if (found != null) return found
  //   }
  //   return null
  // }
  fun findParentForId(id: String): WorkflowNode? {
    if (this.id == id) {
      return null // This is the root node, so it has no parent
    }
    if (children.any { it.id == id }) {
      return this
    }
    return children.firstNotNullOfOrNull {
      it.findParentForId(id)
    }
  }
}

/**
 * Main access point for drawing the workflow tree. This does 2 tasks:
 * 1) places all the nodes
 * 2) draws all the arrows accordingly.
 * Since arrows cannot be drawn with Canvas without an [Offset], we would need to get place all the
 * nodes first, then launch an event to draw the arrows once all nodes have been placed.
 */
@Composable
public fun DrawWorkflowTree(
  root: WorkflowNode,
    translationXArg: Float,
    translationYArg: Float,
    scale: Float,
) {
  val nodePositions = remember { mutableMapOf<String, Offset>() }
  // val nodeCount = remember { mutableStateOf(0) }
  // val nodeMapSize = remember { mutableStateOf(0)}
  // val readyToDraw = remember { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxSize()) {

    drawTree(root, nodePositions
      // , nodeCount, nodeMapSize
    )
    Canvas(modifier = Modifier.fillMaxSize()
    //   .graphicsLayer {
    //   translationX = translationXArg
    //   translationY = translationYArg
    //   scaleX = scale
    //   scaleY = scale
    // }
    ) {
      nodePositions.forEach { (id, position) ->
        val start = position
        val end = root.findParentForId(id)?.let { parent -> nodePositions[parent.id] } ?: return@forEach
        drawLine(
          color = Color.Black,
          start = Offset(start.x.dp.toPx(), start.y.dp.toPx()),
          end = Offset(end.x.dp.toPx(), end.y.dp.toPx()),
          strokeWidth = 2.dp.toPx()
        )
      }
    }
    // LaunchedEffect(nodeMapSize.value) {
    //   if (nodePositions.size == nodeCount.value) {
    //     readyToDraw.value = true
    //   }
    // }

    // if (readyToDraw.value) {
    // LaunchedEffect(nodePositions) {
    //   drawArrows(root, nodePositions)
    //   drawArrows(root, nodePositions)
    //
    // }
    // }
  }
}

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 *
 */
@Composable
private fun drawTree(
  node: WorkflowNode,
  nodePositions: MutableMap<String, Offset>,

  // nodeCount: MutableState<Int>,
  // nodeMapSize: MutableState<Int>
) {

  Column(
    modifier = Modifier.padding(10.dp).border(1.dp,Color.Black),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    drawNode(node, nodePositions
      // , nodeMapSize
    )
    // nodeCount.value += 1
    if (node.children.isEmpty()) return@Column

    // Spacer(modifier = Modifier.padding(30.dp))
    Row (
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Top,
      modifier = Modifier.border(1.dp, Color.Black)
    ) {
      node.children.forEach { childNode ->
        drawTree (childNode, nodePositions
          // , nodeCount, nodeMapSize
        )
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
  nodePositions: MutableMap<String, Offset>,
  // nodeMapSize: MutableState<Int>
) {
  val density = LocalDensity.current

  val open = remember { mutableStateOf(false) }
  Box (
    modifier = Modifier
      // .border(1.dp, Color.Black)
      .clickable { open.value = !open.value }
      .onGloballyPositioned {
        val coords = it.positionInRoot()
        nodePositions[node.id] = with(density) { coords.toDp(density) }
        // nodeMapSize.value += 1
      }
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

@Composable
private fun drawArrows(
  node: WorkflowNode,
  nodePositions: MutableMap<String, Offset>
){
  // if (node.children.isEmpty()) return
  println(nodePositions)
  node.children.forEach { childNode ->
    val parentPosition = nodePositions[node.id] ?: error("Child must have a position")
    val childPosition = nodePositions[childNode.id] ?: error("Child must have a position")

    Arrow(
      start = parentPosition,
      end = childPosition
    )

    drawArrows(childNode, nodePositions)
  }
}
@Composable
fun TreeNode(
  id: String,
  modifier: Modifier = Modifier,
  onPositioned: ((LayoutCoordinates) -> Unit)? = null
) {
  Box(
    modifier = modifier
      .padding(8.dp)
      // .onGloballyPositioned { coords ->
      //   onPositioned?.invoke(coords)
      // }
      .background(Color(0xFFE0F7FA), shape = RoundedCornerShape(8.dp))
      .border(1.dp, Color.Black, shape = RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(text = id)
  }
}

@Preview
@Composable
fun test() {
  val nodePositions = remember { mutableStateMapOf<String, Offset>() }
  val density = LocalDensity.current

  Box(modifier = Modifier.fillMaxSize()) {
    Column {
      TreeNode(
        id = "A",
        modifier = Modifier
          .padding(horizontal = 200.dp)
          .onGloballyPositioned { coordinates ->
            val localOffset = coordinates.positionInParent()
            nodePositions["A"] = with(density) { localOffset.toDp(density) }
          }
      )

      TreeNode(
        id = "B",
        modifier = Modifier
          .onGloballyPositioned { coordinates ->
            val localOffset = coordinates.positionInParent()
            nodePositions["B"] = with(density) { localOffset.toDp(density) }
          }
      )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
      val start = nodePositions["A"]
      val end = nodePositions["B"]
      if (start != null && end != null) {
        // Convert dp to px inside the DrawScope
        drawLine(
          color = Color.Black,
          start = Offset(start.x.dp.toPx(), start.y.dp.toPx()),
          end = Offset(end.x.dp.toPx(), end.y.dp.toPx()),
          strokeWidth = 2.dp.toPx()
        )
      }
    }
  }
}
