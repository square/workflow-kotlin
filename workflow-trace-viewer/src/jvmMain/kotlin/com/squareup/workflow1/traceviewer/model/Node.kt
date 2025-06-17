package com.squareup.workflow1.traceviewer.model

/**
 * Since the logic of Workflow is hierarchical (where each workflow may have parent workflows and/or
 * children workflows, a tree structure is most appropriate for representing the data rather than
 * using flat data structures like an array.
 *
 * TBD what more metadata should be involved with each node, e.g. (props, states, # of render passes)
 */
public class Node(
  val id: String,
  val name: String = "default",
  val props: Any? = null,
  val state: Any? = null,
  val children: List<Node>
)
