package com.squareup.workflow1.traceviewer.model

/**
 * Represents the difference between the current and previous state of a node in the workflow trace.
 * This will be what is passed as a state between UI to display the diff.
 *
 * If it's the first node in the frame, [previous] will be null and there is no difference to show.
 */
internal class NodeUpdate(
  val current: Node,
  val previous: Node?,
)
