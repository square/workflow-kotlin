package com.squareup.workflow1.traceviewer.model

/**
 * Since the logic of Workflow is hierarchical (where each workflow may have parent workflows and/or
 * children workflows, a tree structure is most appropriate for representing the data rather than
 * using flat data structures like an array.
 *
 * TBD what more metadata should be involved with each node, e.g. (props, states, # of render passes)
 */
internal data class Node(
  val name: String,
  val id: String,
  val parent: String,
  val parentId: String,
  val props: String,
  val state: String,
  val rendering: String = "",
  val children: List<Node>,
) {

  fun copy(): Node {
    return Node(
      name = name,
      id = id,
      parent = parent,
      parentId = parentId,
      props = props,
      state = state,
      rendering = rendering,
      children = children,
    )
  }

  override fun toString(): String {
    return "Node(name='$name', parent='$parent', children=${children})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Node) return false
    return this.id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}

internal fun Node.addChild(child: Node): Node {
  return copy( children = this.children + child )
}

internal fun Node.replaceChild(child: Node): Node {
  return copy(children = this.children.map { if (it.id == child.id) child else it })
}
