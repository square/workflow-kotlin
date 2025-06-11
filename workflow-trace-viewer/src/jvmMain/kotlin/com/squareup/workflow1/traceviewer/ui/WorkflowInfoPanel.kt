package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons.AutoMirrored.Filled
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.workflow1.traceviewer.model.Node

/**
 * A panel that displays information about the selected workflow node.
 * It can be toggled open or closed, and resets when the user selects a new file
 *
 * @param selectedNode The currently selected workflow node, or null if no node is selected.
 */
@Composable
public fun InfoPanel(
  selectedNode: Node?,
  modifier: Modifier = Modifier
) {
  // This row is ordered RTL
  Row {
    Spacer(modifier = Modifier.weight(1f))

    var panelOpen by remember { mutableStateOf(false) }

    IconButton(
      onClick = { panelOpen = !panelOpen },
      modifier = Modifier
        .padding(8.dp)
        .size(30.dp)
        .align(Alignment.Top)
    ) {
      Icon(
        imageVector = if (panelOpen) Filled.KeyboardArrowLeft else Filled.KeyboardArrowRight,
        contentDescription = if (panelOpen) "Close Panel" else "Open Panel",
        modifier = Modifier
      )
    }

    // based on open/close, display the node details (Column)
    if (panelOpen) {
      PanelDetails(
        selectedNode,
        Modifier.fillMaxWidth(.35f)
      )
    }
  }
}

/**
 * The text details of the selected node. This should be closely coupled with the [Node]
 * data class to see what information should be displayed.
 */
@Composable
private fun PanelDetails(
  node: Node?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxHeight()
      .background(Color.LightGray)
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (node == null) {
      Text("No node selected")
      return@Column
    }

    Text("only visible with a node selected")
    Text(
      text = "This is a node panel for ${node.name}",
      fontSize = 20.sp,
      modifier = Modifier.padding(8.dp)
    )
  }
}
