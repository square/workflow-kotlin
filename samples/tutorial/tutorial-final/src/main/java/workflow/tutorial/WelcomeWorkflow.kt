package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.TextController
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import workflow.tutorial.WelcomeWorkflow.State

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
    onLogInPressed = context.eventHandler("onLogInPressed") {
      // Don't log in if the name isn't filled in.
      state.name.textValue.takeIf { it.isNotEmpty() }?.let {
        setOutput(LoggedIn(it))
      }
    }
  )

  override fun snapshotState(state: State): Snapshot? = null
}
