package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen
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
    // infrastructure will create a child workflow with state if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
      // When WelcomeWorkflow emits LoggedIn, turn it into our login action.
      login(output.username)
    }

    return when (renderState) {
      // When the state is Welcome, defer to the WelcomeWorkflow.
      is Welcome -> {
        BackStackScreen(welcomeScreen)
      }

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoBackStack = context.renderChild(TodoNavigationWorkflow, TodoProps(renderState.username)) {
          // When receiving a Back output, treat it as a logout action.
          logout
        }
        BackStackScreen(welcomeScreen) + todoBackStack
      }
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun login(username: String) = action {
    state = Todo(username)
  }

  private val logout = action {
    state = Welcome
  }
}
