package com.squareup.workflow1

import com.squareup.workflow1.TreeWorkflow.Rendering

/**
 * A [Workflow] that has a simple string state and can be configured with children at construction.
 */
internal class TreeWorkflow(
  private val name: String,
  private vararg val children: TreeWorkflow
) : StatefulWorkflow<String, String, Nothing, Rendering>() {

  class Rendering(
    val data: String,
    val setData: (String) -> Unit,
    val children: Map<String, Rendering> = emptyMap()
  ) {
    /**
     * Walk this rendering's tree of children to find a rendering.
     *
     * The first argument is looked up in this.[children], the 2nd part, if any, is looked up
     * in that rendering's children, and so on.
     */
    operator fun get(vararg path: String): Rendering {
      return path.fold(this) { node, pathPart ->
        node.children.getValue(pathPart)
      }
    }
  }

  override fun initialState(
    props: String,
    snapshot: Snapshot?
  ): String = snapshot?.bytes?.parse {
    it.readUtf8WithLength()
  } ?: props

  override fun render(
    renderProps: String,
    renderState: String,
    context: RenderContext<String, String, Nothing>
  ): Rendering {
    val childRenderings = children
      .mapIndexed { index, child ->
        val childRendering = context.renderChild(child, "$renderProps[$index]", child.name)
        Pair(child.name, childRendering)
      }
      .toMap()

    return Rendering(
      data = "$name:$renderState",
      setData = { context.actionSink.send(onEvent(it)) },
      children = childRenderings
    )
  }

  override fun snapshotState(state: String): Snapshot =
    Snapshot.write {
      it.writeUtf8WithLength(state)
    }

  private fun onEvent(newState: String) = action("onEvent") {
    state = newState
  }
}
