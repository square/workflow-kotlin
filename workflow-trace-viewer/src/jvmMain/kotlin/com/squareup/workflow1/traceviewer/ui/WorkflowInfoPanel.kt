package com.squareup.workflow1.traceviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
public fun RightInfoPanel(
  selectedNode: Node?,
  modifier: Modifier = Modifier
) {
  // This row is aligned to the right of the screen.
  Row(
    modifier = modifier
  ) {
    var panelOpen by remember { mutableStateOf(false) }

    IconButton(
      onClick = { panelOpen = !panelOpen },
      modifier = Modifier
        .padding(8.dp)
        .size(30.dp)
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

@Composable
private fun NodePanelDetails(
  node: Node?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxHeight()
      .background(Color.LightGray)
      .padding(8.dp)
      .pointerInput(Unit) {
        detectTapGestures { }
      },
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (node == null) {
      Text("No node selected")
      return@Column
    }

    val textModifier = Modifier.padding(8.dp)
    val textStyle = TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center)
    val fields = mapOf(
      "Name" to node.name,
      "ID" to node.id,
      "Props" to node.props.toString(),
      "State" to node.state.toString(),
      "Renderings" to node.renderings.toString()
    )

    fields.forEach { (label, value) ->
      Text(
        text = "$label: $value",
        modifier = textModifier,
        style = textStyle
      )
    }
  }
}
