package com.squareup.workflow1.traceviewer.model

/**
 * Since the logic of Workflow is hierarchical (where each workflow may have parent workflows and/or
 * children workflows, a tree structure is most appropriate for representing the data rather than
 * using flat data structures like an array.
 *
 * TBD what more metadata should be involved with each node, e.g. (props, states, # of render passes)
 */
public class Node(
  val name: String,
  val parent: String,
  val props: Any? = null,
  val state: Any? = null,
  val renderings: Any? = null,
  val children: List<Node>,
  val id: String
) {
  override fun toString(): String {
    return "Node(name='$name', parent='$parent', children=${children.size})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Node) return false
    return this.id == other.id
  }
  override fun hashCode(): Int { return id.hashCode() }
}
