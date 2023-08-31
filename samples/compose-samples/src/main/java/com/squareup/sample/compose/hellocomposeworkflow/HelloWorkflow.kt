package com.squareup.sample.compose.hellocomposeworkflow

import com.squareup.sample.compose.hellocomposeworkflow.HelloWorkflow.State
import com.squareup.sample.compose.hellocomposeworkflow.HelloWorkflow.State.Goodbye
import com.squareup.sample.compose.hellocomposeworkflow.HelloWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowLocal
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ComposeScreen

/**
 * The root workflow of this sample. Manges the current toggle state and passes it to
 * [HelloComposeWorkflow].
 */
@OptIn(WorkflowUiExperimentalApi::class)
object HelloWorkflow : StatefulWorkflow<Unit, State, Nothing, ComposeScreen>() {
  enum class State {
    Hello,
    Goodbye;

    fun theOtherState(): State = when (this) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }

  private val helloAction = action {
    state = state.theOtherState()
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?,
    workflowLocal: WorkflowLocal
  ): State = snapshot?.bytes?.parse { source -> if (source.readInt() == 1) Hello else Goodbye }
    ?: Hello

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): ComposeScreen =
    context.renderChild(HelloComposeWorkflow, renderState.name) { helloAction }

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)
}
