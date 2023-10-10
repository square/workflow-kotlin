package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.WelcomeWorkflow.Output
import workflow.tutorial.WelcomeWorkflow.State

object WelcomeWorkflow : StatefulWorkflow<Unit, State, Output, WelcomeScreen>() {

  data class State(
    val prompt: String
  )

  object Output

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State(prompt = "")

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
    promptText = renderState.prompt,
    onLogInTapped = context.eventHandler("onLogInTapped") { name ->
      state = when {
        name.isEmpty() -> state.copy(prompt = "name required to log in")
        else -> state.copy(prompt = "logging in as \"$name\"…")
      }
    }
  )

  override fun snapshotState(state: State): Snapshot? = null
}
