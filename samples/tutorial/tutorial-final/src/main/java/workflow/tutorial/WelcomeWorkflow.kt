package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import workflow.tutorial.WelcomeWorkflow.State

object WelcomeWorkflow : StatefulWorkflow<Unit, State, LoggedIn, WelcomeScreen>() {

  data class State(
    val name: String
  )

  data class LoggedIn(val username: String)

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State(name = "")

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
      username = renderState.name,
      onUsernameChanged = { context.actionSink.send(onNameChanged(it)) },
      onLoginTapped = {
        // Whenever the login button is tapped, emit the onLogin action.
        context.actionSink.send(onLogin())
      }
  )

  // Needs to be internal so we can access it from the tests.
  internal fun onNameChanged(name: String) = action {
    state = state.copy(name = name)
  }

  internal fun onLogin() = action {
    // Don't log in if the name isn't filled in.
    if (state.name.isNotEmpty()) {
      setOutput(LoggedIn(state.name))
    }
  }

  override fun snapshotState(state: State): Snapshot? = null
}
