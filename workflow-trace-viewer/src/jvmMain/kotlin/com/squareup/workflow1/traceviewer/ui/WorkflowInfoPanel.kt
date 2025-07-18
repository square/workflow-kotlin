package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.traceviewer.model.Node
import com.squareup.workflow1.traceviewer.model.NodeUpdate
import kotlin.reflect.full.memberProperties

/**
 * A panel that displays information about the selected workflow node.
 * It can be toggled open or closed, and resets when the user selects a new file
 *
 * @param selectedNode The currently selected workflow node, or null if no node is selected.
 */
@Composable
internal fun RightInfoPanel(
  selectedNode: NodeUpdate?,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
  ) {
    var panelOpen by remember { mutableStateOf(false) }

    IconButton(
      onClick = { panelOpen = !panelOpen },
      modifier = Modifier
        .padding(8.dp)
        .size(40.dp)
        .align(Alignment.Top)
    ) {
      Icon(
        imageVector = if (panelOpen) Filled.KeyboardArrowRight else Filled.KeyboardArrowLeft,
        contentDescription = if (panelOpen) "Close Panel" else "Open Panel",
        modifier = Modifier
      )
    }

    if (panelOpen) {
      NodePanelDetails(
        selectedNode,
        Modifier.fillMaxWidth(.30f)
      )
    }
  }
}

/**
 * Displays specific details about the opened workflow node.
 */
@Composable
private fun NodePanelDetails(
  node: NodeUpdate?,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxHeight()
      .background(Color.White)
      .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    if (node == null) {
      item {
        Text("Select a node to view details")
      }
      return@LazyColumn
    }
    item {
      Text(
        text = "Workflow Details",
        style = MaterialTheme.typography.h6,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
      )
    }

    val fields = Node::class.memberProperties
    for (field in fields) {
      val currVal = field.get(node.current).toString()
      val pastVal = if (node.previous != null) field.get(node.previous).toString() else null
      item {
        DetailCard(
          label = field.name,
          currValue = currVal,
          pastValue = pastVal
        )
      }
    }
  }
}

/**
 * Card component that represents each item for the nodes.
 *
 * Can be open/closed to show/hide details.
 */
@Composable
private fun DetailCard(
  label: String,
  currValue: String,
  pastValue: String?
) {
  var open by remember { mutableStateOf(true) }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp)
      .clickable {
        open = !open
      },
    elevation = 3.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.h6,
        color = Color.Black,
        fontWeight = FontWeight.Medium
      )
      if (!open) {
        return@Card
      }

      Spacer(modifier = Modifier.height(4.dp))
      if (pastValue != null) {
        Column {
          Text(
            text = "Before:",
            style = TextStyle(fontStyle = FontStyle.Italic),
            color = Color.Black,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = pastValue,
            style = MaterialTheme.typography.body2,
            color = Color.Black
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "After:",
            style = TextStyle(fontStyle = FontStyle.Italic),
            color = Color.Black,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = currValue,
            style = MaterialTheme.typography.body2,
            color = Color.Black
          )
        }
      } else {
        Text(
          text = currValue,
          style = MaterialTheme.typography.body1,
          color = Color.Black
        )
      }
    }
  }
}
