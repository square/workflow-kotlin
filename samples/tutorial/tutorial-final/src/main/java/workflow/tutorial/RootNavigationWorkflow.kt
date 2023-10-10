package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.plus
import workflow.tutorial.RootNavigationWorkflow.State
import workflow.tutorial.RootNavigationWorkflow.State.Todo
import workflow.tutorial.RootNavigationWorkflow.State.Welcome
import workflow.tutorial.TodoNavigationWorkflow.TodoProps

@OptIn(WorkflowUiExperimentalApi::class)
object RootNavigationWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<*>>() {

  sealed class State {
    object Welcome : State()
    data class Todo(val username: String) : State()
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = Welcome

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): BackStackScreen<*> {
    // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
    // infrastructure will start a child workflow session if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { loggedIn ->
      // When WelcomeWorkflow emits LoggedIn, enqueue our log in action.
      logIn(loggedIn.username)
    }

    return when (renderState) {
      // When the state is Welcome, defer to the WelcomeWorkflow.
      is Welcome -> {
        BackStackScreen(welcomeScreen)
      }

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoBackStack = context.renderChild(
          child = TodoNavigationWorkflow,
          props = TodoProps(renderState.username)
        ) {
          // When TodoNavigationWorkflow emits Back, enqueue our log out action.
          logOut
        }
        BackStackScreen(welcomeScreen) + todoBackStack
      }
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun logIn(username: String) = action {
    state = Todo(username)
  }

  private val logOut = action {
    state = Welcome
  }
}
