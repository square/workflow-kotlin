package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import workflow.tutorial.WelcomeWorkflow.Output
import workflow.tutorial.WelcomeWorkflow.State

object WelcomeWorkflow : StatefulWorkflow<Unit, State, LoggedIn, WelcomeScreen>() {

  data class LoggedIn(val userName: String)

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
      onUsernameChanged = {
        println("FTK here")
        context.actionSink.send(onUserNameChanged(it))
      },
      onLoginTapped = {
        context.actionSink.send(onLogin())
      }
    )

    println("FTK render renderState :$renderState")
    println("FTK render WelcomeScreen :$screen")

    return screen
  }

  private fun onUserNameChanged(userName: String) =
    action {
      state = state.copy(username = userName + "a")
      println("FTK onUserNameChanged: $state")
      state
    }

  private fun onLogin() = action {
    setOutput(LoggedIn(state.username))
  }

  override fun snapshotState(state: State): Snapshot? = Snapshot.write {
    TODO("Save state")
  }
}
