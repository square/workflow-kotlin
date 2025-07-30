package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate

/**
 * Since the workflow nodes present a tree structure, we utilize a recursive function to draw the tree
 * The Column holds a subtree of nodes, and the Row holds all the children of the current node
 *
 * A mutable map is used to persist the expansion state of the nodes, allowing them to be open and
 * closed from user clicks.
 */
@Composable
internal fun DrawTree(
  node: Node,
  previousFrameNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (NodeUpdate) -> Unit,
  storeNodeLocation: (Node, Offset) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .padding(6.dp)
      .fillMaxSize()
      .then(
        if (node.children.isNotEmpty()) Modifier.border(3.dp, Color.Black) else Modifier
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val isAffected = affectedNodes.contains(node)
    // By default, nodes that relevant to this specific frame are expanded. All others are closed.
    LaunchedEffect(expandedNodes) {
      expandedNodes[node.id] = isAffected
    }
    val isExpanded = expandedNodes[node.id] == true

    DrawNode(
      node = node,
      nodePast = previousFrameNode,
      isAffected = isAffected,
      isExpanded = isExpanded,
      onNodeSelect = onNodeSelect,
      onExpandToggle = { expandedNodes[node.id] = !expandedNodes[node.id]!! },
      storeNodeLocation = storeNodeLocation
    )

    if (isExpanded) {
      val (affectedChildren, unaffectedChildren) = node.children.values
        .partition { affectedNodes.contains(it) }

      UnaffectedChildrenGroup(
        node = node,
        children = unaffectedChildren,
        previousFrameNode = previousFrameNode,
        affectedNodes = affectedNodes,
        expandedNodes = expandedNodes,
        onNodeSelect = onNodeSelect,
        storeNodeLocation = storeNodeLocation
      )

      AffectedChildrenGroup(
        children = affectedChildren,
        previousFrameNode = previousFrameNode,
        affectedNodes = affectedNodes,
        expandedNodes = expandedNodes,
        onNodeSelect = onNodeSelect,
        storeNodeLocation = storeNodeLocation
      )
    }
  }
}

/**
 * Draws the group of unaffected children, which can be open and closed to expand/collapse them.
 *
 * If an unaffected children also has other children, it cannot be opened since the this group
 * treats all nodes as one entity. The right click for the whole group overrides the right click for
 * the individual nodes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UnaffectedChildrenGroup(
  node: Node,
  children: List<Node>,
  previousFrameNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (NodeUpdate) -> Unit,
  storeNodeLocation: (Node, Offset) -> Unit
) {
  if (children.isEmpty()) return

  val groupKey = "${node.id}_unaffected_group"
  LaunchedEffect(Unit) {
    expandedNodes[groupKey] = false
  }
  val isGroupExpanded = expandedNodes[groupKey] == true

  Box(
    modifier = Modifier
      .onPointerEvent(PointerEventType.Press) {
        if (it.buttons.isSecondaryPressed) {
          expandedNodes[groupKey] = !isGroupExpanded
        }
      }
  ) {
    if (!isGroupExpanded) {
      Column(
        modifier = Modifier
          .background(Color.LightGray.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
          .border(1.dp, Color.Gray)
          .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(text = "${node.name}'s", color = Color.DarkGray)
        Text(
          text = "${children.size} unaffected children",
          color = Color.DarkGray,
          fontSize = 12.sp
        )
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        DrawChildrenInGroups(
          children = children,
          previousFrameNode = previousFrameNode,
          affectedNodes = affectedNodes,
          expandedNodes = expandedNodes,
          unaffected = true,
          onNodeSelect = onNodeSelect,
          storeNodeLocation = storeNodeLocation
        )
      }
    }
  }
}

/**
 * Draws the group of affected children
 */
@Composable
private fun AffectedChildrenGroup(
  children: List<Node>,
  previousFrameNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (NodeUpdate) -> Unit,
  storeNodeLocation: (Node, Offset) -> Unit
) {
  if (children.isEmpty()) return

  DrawChildrenInGroups(
    children = children,
    previousFrameNode = previousFrameNode,
    affectedNodes = affectedNodes,
    expandedNodes = expandedNodes,
    onNodeSelect = onNodeSelect,
    storeNodeLocation = storeNodeLocation
  )
}

/**
 * Draws the children in a grid manner, to avoid horizontal clutter and make better use of space.
 *
 * Unaffected children group would call this with `unaffected = true`, which means that simple/nested
 * nodes don't matter since we can't open nested ones, so we just simply group in 5's
 */
@Composable
private fun DrawChildrenInGroups(
  children: List<Node>,
  previousFrameNode: Node?,
  affectedNodes: Set<Node>,
  expandedNodes: MutableMap<String, Boolean>,
  onNodeSelect: (NodeUpdate) -> Unit,
  storeNodeLocation: (Node, Offset) -> Unit,
  unaffected: Boolean = false,
) {
  // Split children into those with children (nested) and those without
  var (nestedChildren, simpleChildren) = children.partition { it.children.isNotEmpty() }

  // Just reset the lists so we chunk everything in the unaffected group
  if (unaffected) {
    nestedChildren = emptyList()
    simpleChildren = children
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Draw simple children in a grid at the top
    if (simpleChildren.isNotEmpty()) {
      val groupedSimpleChildren = simpleChildren.chunked(5)

      groupedSimpleChildren.forEach { group ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .align(Alignment.CenterHorizontally),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Top
        ) {
          group.forEach { childNode ->
            DrawTree(
              node = childNode,
              previousFrameNode = previousFrameNode?.children?.get(childNode.id),
              affectedNodes = affectedNodes,
              expandedNodes = expandedNodes,
              onNodeSelect = onNodeSelect,
              storeNodeLocation = storeNodeLocation
            )
          }
        }
      }
    }

    // Draw nested children in a single row at the bottom
    if (nestedChildren.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
      ) {
        nestedChildren.forEach { childNode ->
          DrawTree(
            node = childNode,
            previousFrameNode = previousFrameNode?.children?.get(childNode.id),
            affectedNodes = affectedNodes,
            expandedNodes = expandedNodes,
            onNodeSelect = onNodeSelect,
            storeNodeLocation = storeNodeLocation
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
  nodePast: Node?,
  isAffected: Boolean,
  isExpanded: Boolean,
  onNodeSelect: (NodeUpdate) -> Unit,
  onExpandToggle: (Node) -> Unit,
  storeNodeLocation: (Node, Offset) -> Unit
) {
  val nodeUpdate = NodeUpdate.create(
    current = node,
    past = nodePast,
    isAffected = isAffected
  )

  Box(
    modifier = Modifier
      .background(nodeUpdate.state.color)
      .clickable {
        onNodeSelect(nodeUpdate)
      }
      .onPointerEvent(PointerEventType.Press) {
        if (it.buttons.isSecondaryPressed) {
          onExpandToggle(node)
        }
      }
      .padding(16.dp)
      .onGloballyPositioned { coords ->
        val offsetToTopLeft = coords.positionInRoot()
        val offsetToCenter = Offset(
          x = offsetToTopLeft.x + coords.size.width / 2,
          y = offsetToTopLeft.y + coords.size.height / 2
        )
        storeNodeLocation(node, offsetToCenter)
      }
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
