package com.squareup.sample.compose.nestedrenderings

import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.LegacyRendering
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowLocal
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A simple workflow that produces [Rendering]s of zero or more children.
 * The rendering provides event handlers for adding children and resetting child count to zero.
 *
 * Every other (odd) rendering in the [Rendering.children] will be wrapped with a [LegacyRendering]
 * to force it to go through the legacy view layer. This way this sample both demonstrates pass-
 * through Composable renderings as well as adapting in both directions.
 */
@OptIn(WorkflowUiExperimentalApi::class)
object RecursiveWorkflow : StatefulWorkflow<Unit, State, Nothing, Screen>() {

  data class State(val children: Int = 0)

  /**
   * A rendering from a [RecursiveWorkflow].
   *
   * @param children A list of renderings to display as children of this rendering.
   * @param onAddChildClicked Adds a child to [children].
   * @param onResetClicked Resets [children] to an empty list.
   */
  data class Rendering(
    val children: List<Screen>,
    val onAddChildClicked: () -> Unit,
    val onResetClicked: () -> Unit
  ) : Screen

  /**
   * Wrapper around a [Rendering] that will be implemented using a legacy view.
   */
  data class LegacyRendering(val rendering: Screen) : Screen

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?,
    workflowLocal: WorkflowLocal
  ): State = State()

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): Rendering {
    return Rendering(
      children = List(renderState.children) { i ->
        val child = context.renderChild(RecursiveWorkflow, key = i.toString())
        if (i % 2 == 0) child else LegacyRendering(child)
      },
      onAddChildClicked = { context.actionSink.send(addChild()) },
      onResetClicked = { context.actionSink.send(reset()) }
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun addChild() = action {
    state = state.copy(children = state.children + 1)
  }

  private fun reset() = action {
    state = State()
  }
}
