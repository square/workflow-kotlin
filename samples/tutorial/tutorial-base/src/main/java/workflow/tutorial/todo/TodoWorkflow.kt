package workflow.tutorial.todo

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.todo.TodoWorkflow.Back
import workflow.tutorial.todo.TodoWorkflow.State
import workflow.tutorial.todo.TodoWorkflow.State.Step
import workflow.tutorial.todo.TodoWorkflow.State.Step.Edit

import workflow.tutorial.todo.TodoWorkflow.TodoProps
import workflow.tutorial.todoedit.TodoEditWorkflow
import workflow.tutorial.todoedit.TodoEditWorkflow.EditProps
import workflow.tutorial.todoedit.TodoEditWorkflow.Output.Discard
import workflow.tutorial.todoedit.TodoEditWorkflow.Output.Save
import workflow.tutorial.todolist.ToDoListWorkflow
import workflow.tutorial.todolist.ToDoListWorkflow.ListProps
import workflow.tutorial.todolist.ToDoListWorkflow.Output
import workflow.tutorial.todolist.ToDoListWorkflow.Output.SelectTodo
import workflow.tutorial.todolist.ToDoListWorkflow.TodoModel

object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  data class TodoProps(
    val username: String
  )

  data class State(
    val todos: List<TodoModel>,
    val step: Step
  ) {
    sealed class Step {
      object List : Step()
      data class Edit(val index: Int) : Step()
    }
  }

  object Back

  private fun discardChanges() = action {
    state = state.copy(step = Step.List)
  }

  private fun saveChanges(
    todo: TodoModel,
    index: Int
  ) = action {
    state = state.copy(
      todos = state.todos.toMutableList().also { it[index] = todo },
      step = Step.List
    )
  }

  override fun initialState(
    props: TodoProps,
    snapshot: Snapshot?
  ): State = State(
    todos = listOf(
      TodoModel(
        title = "Take the cat for a walk",
        note = "Cats really need their outside sunshine time. Don't forget to walk " +
          "Charlie. Hamilton is less excited about the prospect."
      )
    ), step = Step.List
  )

  override fun render(
    renderProps: TodoProps,
    renderState: State,
    context: RenderContext
  ): List<Any> {
    val todListScreen = context.renderChild(
      ToDoListWorkflow,
      props = ListProps(username = renderProps.username, todos = renderState.todos)
    ) { output ->
      when (output) {
        Output.Back -> onBack()
        is SelectTodo -> editTodo(output.index)
      }
    }

    return when (val step = renderState.step) {
      Step.List -> listOf(todListScreen)
      is Edit -> {
        val todoEditScreen = context.renderChild(
          TodoEditWorkflow,
          EditProps(renderState.todos[step.index])
        ) { output ->
          when (output) {
            Discard -> discardChanges()
            is Save -> saveChanges(output.todo, step.index)
          }
        }
        return listOf(todListScreen, todoEditScreen)
      }
    }
  }

  private fun onBack() = action {
    setOutput(Back)
  }

  private fun editTodo(index: Int) = action {
    state = state.copy(step = Step.Edit(index))
  }

  override fun snapshotState(state: State): Snapshot? = Snapshot.write {
    TODO("Save state")
  }
}
