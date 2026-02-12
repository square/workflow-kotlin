package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.toBackStackScreen
import workflow.tutorial.RootNavigationWorkflow.State
import workflow.tutorial.RootNavigationWorkflow.State.ShowingTodo
import workflow.tutorial.RootNavigationWorkflow.State.ShowingWelcome
import workflow.tutorial.TodoListWorkflow.ListProps

object RootNavigationWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<*>>() {

  sealed interface State {
    object ShowingWelcome : State
    data class ShowingTodo(val username: String) : State
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = ShowingWelcome

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext<Unit, State, Nothing>
  ): BackStackScreen<*> {
    // We always render the welcomeScreen regardless of the current state.
    // It's either showing or else we may want to pop back to it.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { loggedIn ->
      // When WelcomeWorkflow emits LoggedIn, enqueue our log in action.
      logIn(loggedIn.username)
    }

    return when (renderState) {
      is ShowingWelcome -> {
        BackStackScreen(welcomeScreen)
      }

      is ShowingTodo -> {
        val todoBackStack = context.renderChild(
          child = TodoListWorkflow,
          props = ListProps(renderState.username),
          handler = {
            // When TodoNavigationWorkflow emits Back, enqueue our log out action.
            logOut
          }
        )
        listOf(welcomeScreen, todoBackStack).toBackStackScreen()
      }
    }
  }
  override fun snapshotState(state: State): Snapshot? = null

  private fun logIn(username: String) = action("logIn") {
    state = ShowingTodo(username)
  }

  private val logOut = action("logOut") {
    state = ShowingWelcome
  }
}
