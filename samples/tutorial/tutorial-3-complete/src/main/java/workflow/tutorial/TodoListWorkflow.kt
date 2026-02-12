package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.Screen
import workflow.tutorial.TodoEditWorkflow.Output.DiscardChanges
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoListWorkflow.BackPressed
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.State
import workflow.tutorial.TodoListWorkflow.State.Step

object TodoListWorkflow : StatefulWorkflow<ListProps, State, BackPressed, List<Screen>>() {

  data class ListProps(val username: String)

  data class TodoModel(
    val title: String,
    val note: String
  )

  data class State(
    val todos: List<TodoModel>,
    val step: Step
  ) {
    sealed interface Step {
      /** Showing the list of items. */
      object ShowList : Step

      /**
       * Editing a single item. The state holds the index
       * so it can be updated when a save action is received.
       */
      data class EditItem(val index: Int) : Step
    }
  }

  object BackPressed

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ) = State(
    todos = listOf(
      TodoModel(
        title = "Take the cat for a walk",
        note = "Cats really need their outside sunshine time. Don't forget to walk " +
          "Charlie. Hamilton is less excited about the prospect."
      )
    ),
    step = Step.ShowList
  )

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext<ListProps, State, BackPressed>
  ): List<Screen> {
    val titles = renderState.todos.map { it.title }
    val todoListScreen = TodoListScreen(
      username = renderProps.username,
      todoTitles = titles,
      onBackPressed = context.eventHandler("onBackPressed") { setOutput(BackPressed) },
      onRowPressed = context.eventHandler("onRowPressed") { index ->
        // When a todo item is selected, edit it.
        state = state.copy(step = Step.EditItem(index))
      }
    )

    return when (val step = renderState.step) {
      // On the "list" step, return just the list screen.
      Step.ShowList -> listOf(todoListScreen)

      // On the "edit" step, return both the list and edit screens.
      is Step.EditItem -> {
        val todoEditScreen = context.renderChild(
          TodoEditWorkflow,
          props = TodoEditWorkflow.EditProps(renderState.todos[step.index])
        ) { output ->
          when (output) {
            // Send the discardChanges action when the discard output is received.
            DiscardChanges -> discardChanges()

            // Send the saveChanges action when the save output is received.
            is SaveChanges -> saveChanges(output.todo, step.index)
          }
        }

        listOf(todoListScreen, todoEditScreen)
      }
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun discardChanges() = action("discardChanges") {
    // Discard changes by simply returning to the list.
    state = state.copy(step = Step.ShowList)
  }

  private fun saveChanges(
    todo: TodoModel,
    index: Int
  ) = action("saveChanges") {
    // To save changes update the state of the item at index and return to the list.
    state = state.copy(
      todos = state.todos.toMutableList().also { it[index] = todo },
      step = Step.ShowList
    )
  }
}
