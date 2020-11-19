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
    props: Unit,
    state: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
    username = state.username,
    onUsernameChanged = { context.actionSink.send(onUserameChanged(it)) },
    onLoginTapped = {}
  )

  private fun onUserameChanged(username: String) = action {
    state = state.copy(username = username)
  }

  override fun snapshotState(state: State): Snapshot? = null
}
