package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.WelcomeWorkflow.Output
import workflow.tutorial.WelcomeWorkflow.State

object WelcomeWorkflow : StatefulWorkflow<Unit, State, Output, WelcomeScreen>() {

  data class State(
    val name: String
  )

  object Output

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State(name = "")

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
    name = state.name,
    onNameChanged = { context.actionSink.send(onNameChanged(it)) },
    onLoginTapped = {}
  )

  private fun onNameChanged(name: String) = action {
    state = state.copy(name = name)
  }

  override fun snapshotState(state: State): Snapshot? = null
}
