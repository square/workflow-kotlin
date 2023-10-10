package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import workflow.tutorial.WelcomeWorkflow.State

@OptIn(WorkflowUiExperimentalApi::class)
object WelcomeWorkflow : StatefulWorkflow<Unit, State, LoggedIn, WelcomeScreen>() {

  data class State(
    val name: TextController
  )

  data class LoggedIn(val username: String)

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State(name = TextController(""))

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
    username = renderState.name,
    onLoginTapped = {
      // Whenever the login button is tapped, emit the onLogin action.
      context.actionSink.send(onLogin())
    }
  )

  internal fun onLogin() = action {
    // Don't log in if the name isn't filled in.
    state.name.textValue.takeIf { it.isNotEmpty() }?.let {
      setOutput(LoggedIn(it))
    }
  }

  override fun snapshotState(state: State): Snapshot? = null
}
