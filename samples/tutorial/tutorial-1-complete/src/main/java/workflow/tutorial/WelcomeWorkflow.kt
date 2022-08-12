package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.WelcomeWorkflow.Output
import workflow.tutorial.WelcomeWorkflow.State

object WelcomeWorkflow : StatefulWorkflow<Unit, State, Output, WelcomeScreen>() {

  data class State(
    val username: String
  )

  object Output

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State(username = "")

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
    username = renderState.username,
    onUsernameChanged = { context.actionSink.send(onUsernameChanged(it)) },
    onLoginTapped = {}
  )

  private fun onUsernameChanged(username: String) = action {
    state = state.copy(username = username + "a")
  }

  override fun snapshotState(state: State): Snapshot? = null
}
