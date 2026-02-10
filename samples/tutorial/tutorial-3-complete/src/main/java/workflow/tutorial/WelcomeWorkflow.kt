package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.Screen
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import workflow.tutorial.WelcomeWorkflow.State

object WelcomeWorkflow : StatefulWorkflow<Unit, State, LoggedIn, Screen>() {

  data class State(
    val prompt: String
  )

  data class LoggedIn(val username: String)

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State(prompt = "")

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext<Unit, State, LoggedIn>
  ): WelcomeScreen = WelcomeScreen(
    promptText = renderState.prompt,
    onLogInTapped = context.eventHandler("onLogInTapped") { name ->
      if (name.isEmpty()) {
        state = state.copy(prompt = "name required to log in")
      } else {
        setOutput(LoggedIn(name))
      }
    }
  )

  override fun snapshotState(state: State): Snapshot? = null
}
