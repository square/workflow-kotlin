package com.squareup.workflow1.traceviewer.model

import androidx.compose.ui.graphics.Color

/**
 * Represents the difference between the current and previous state of a node in the workflow trace.
 * This will be what is passed as a state between UI to display the diff. The states all have an
 * associated color
 *
 * If it's the first node in the frame, [past] will be null and there is no difference to show.
 */
internal data class NodeUpdate(
  val current: Node,
  val past: Node?,
  val state: NodeState
) {
  companion object {
    fun create(current: Node, past: Node?, isAffected: Boolean): NodeUpdate {
      val state = when {
        !isAffected -> NodeState.UNCHANGED
        past == null -> NodeState.NEW
        current.props != past.props -> NodeState.PROPS_CHANGED
        current.state != past.state -> NodeState.STATE_CHANGED
        else -> NodeState.CHILDREN_CHANGED
      }

      return NodeUpdate(current, past, state)
    }
  }
}

enum class NodeState(val color: Color) {
  NEW(Color(0x804CAF50)), // green
  STATE_CHANGED(Color(0xFFE57373)), // red
  PROPS_CHANGED(Color(0xFFFF8A65)), // orange
  CHILDREN_CHANGED(Color(0x802196F3)), // blue
  UNCHANGED(Color.LightGray.copy(alpha = 0.3f)),
}
