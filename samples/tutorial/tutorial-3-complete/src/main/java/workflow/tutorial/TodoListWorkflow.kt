package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.TodoEditWorkflow.Output.Discard
import workflow.tutorial.TodoEditWorkflow.Output.Save
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoListWorkflow.Back
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.State
import workflow.tutorial.TodoListWorkflow.State.Step

@OptIn(WorkflowUiExperimentalApi::class)
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Any>>() {

  data class ListProps(val name: String)

  data class State(
    val todos: List<TodoModel>,
    val step: Step
  ) {
    sealed class Step {
      /** Showing the list of items. */
      object List : Step()

      /**
       * Editing a single item. The state holds the index so it can be updated when a save action is
       * received.
       */
      data class Edit(val index: Int) : Step()
    }
  }

  object Back

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
      step = Step.List
  )

  override fun render(
    props: ListProps,
    state: State,
    context: RenderContext
  ): List<Any> {
    val titles = state.todos.map { it.title }
    val todoListScreen = TodoListScreen(
        name = props.name,
        todoTitles = titles,
        onTodoSelected = { context.actionSink.send(selectTodo(it)) },
        onBack = { context.actionSink.send(onBack()) }
    )

    return when (val step = state.step) {
      // On the "list" step, return just the list screen.
      Step.List -> listOf(todoListScreen)
      is Step.Edit -> {
        // On the "edit" step, return both the list and edit screens.
        val todoEditScreen = context.renderChild(
            TodoEditWorkflow,
            props = EditProps(state.todos[step.index])
        ) { output ->
          when (output) {
            // Send the discardChanges action when the discard output is received.
            Discard -> discardChanges()
            // Send the saveChanges action when the save output is received.
            is Save -> saveChanges(output.todo, step.index)
          }
        }
        return listOf(todoListScreen, todoEditScreen)
      }
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun onBack() = action {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun selectTodo(index: Int) = action {
    // When a todo item is selected, edit it.
    state = state.copy(step = Step.Edit(index))
  }

  private fun discardChanges() = action {
    // When a discard action is received, return to the list.
    state = state.copy(step = Step.List)
  }

  private fun saveChanges(
    todo: TodoModel,
    index: Int
  ) = action {
    // When changes are saved, update the state of that todo item and return to the list.
    state = state.copy(
        todos = state.todos.toMutableList().also { it[index] = todo },
        step = Step.List
    )
  }
}
