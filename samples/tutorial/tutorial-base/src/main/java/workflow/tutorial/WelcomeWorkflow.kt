package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
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
  ): WelcomeScreen {

    val screen = WelcomeScreen(
      userName = renderState.username,
      onUsernameChanged = {},
      onLoginTapped = {}
    )

    return screen
  }

  override fun snapshotState(state: State): Snapshot? = Snapshot.write {
    TODO("Save state")
  }
}
