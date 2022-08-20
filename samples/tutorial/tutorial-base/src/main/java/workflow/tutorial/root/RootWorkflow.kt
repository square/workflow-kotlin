@file:OptIn(WorkflowUiExperimentalApi::class)

package workflow.tutorial.root

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.backstack.toBackStackScreen
import workflow.tutorial.WelcomeWorkflow

import workflow.tutorial.root.RootWorkflow.State
import workflow.tutorial.root.RootWorkflow.State.Todo
import workflow.tutorial.root.RootWorkflow.State.Welcome
import workflow.tutorial.todolist.ToDoListWorkflow
import workflow.tutorial.todolist.ToDoListWorkflow.ListProps

object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<Any>>() {

  sealed class State {
    object Welcome : State()
    data class Todo(val userName: String) : State()
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = Welcome

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): BackStackScreen<Any> {

    val backStackScreens = mutableListOf<Any>()

    val welcomeScreen = context.renderChild(WelcomeWorkflow, Unit) { output ->
      login(output.userName)
    }

    backStackScreens += welcomeScreen

    when (renderState) {
      is Welcome -> {
        // We always add the welcome screen to the backstack, so this is a no op.
      }

      is Todo -> {
        val todoScreen =
          context.renderChild(child = ToDoListWorkflow, props = ListProps(renderState.userName)) {
            logout()
          }

        backStackScreens += todoScreen

      }
    }

    return backStackScreens.toBackStackScreen()
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun login(username: String) = action {
    state = Todo(username)
  }

  private fun logout() = action {
    state = Welcome
  }
}
