package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.toBackStackScreen
import workflow.tutorial.RootWorkflow.State
import workflow.tutorial.RootWorkflow.State.Todo
import workflow.tutorial.RootWorkflow.State.Welcome
import workflow.tutorial.TodoWorkflow.TodoProps

@OptIn(WorkflowUiExperimentalApi::class)
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<Any>>() {

  sealed class State {
    object Welcome : State()
    data class Todo(val name: String) : State()
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
  ): BackStackScreen<Any> {

    // Our list of back stack items. Will always include the "WelcomeScreen".
    val backstackScreens = mutableListOf<Any>()

    // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
    // infrastructure will create a child workflow with state if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
      // When WelcomeWorkflow emits LoggedIn, turn it into our login action.
      login(output.username)
    }
    backstackScreens += welcomeScreen

    when (renderState) {
      // When the state is Welcome, defer to the WelcomeWorkflow.
      is Welcome -> {
        // We always add the welcome screen to the backstack, so this is a no op.
      }

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoListScreens = context.renderChild(TodoWorkflow, TodoProps(renderState.name)) {
          // When receiving a Back output, treat it as a logout action.
          logout
        }
        backstackScreens.addAll(todoListScreens)
      }
    }

    // Finally, return the BackStackScreen with a list of BackStackScreen.Items
    return backstackScreens.toBackStackScreen()
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun login(name: String) = action {
    state = Todo(name)
  }

  private val logout = action {
    state = Welcome
  }
}
