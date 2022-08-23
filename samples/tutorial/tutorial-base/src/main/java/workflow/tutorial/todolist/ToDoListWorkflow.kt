package workflow.tutorial.todolist

import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.todolist.ToDoListWorkflow.ListProps
import workflow.tutorial.todolist.ToDoListWorkflow.Output
import workflow.tutorial.todolist.ToDoListWorkflow.Output.Back
import workflow.tutorial.todolist.ToDoListWorkflow.Output.SelectTodo

object ToDoListWorkflow : StatelessWorkflow<ListProps, Output, ToDoListScreen>() {

  sealed class Output {
    object Back : Output()
    data class SelectTodo(val index: Int) : Output()
  }

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  data class TodoModel(
    val title: String,
    val note: String
  )

  object State

  override fun render(
    renderProps: ListProps,
    context: RenderContext
  ): ToDoListScreen {
    val titles = renderProps.todos.map { it.title }

    val todoListScreen = ToDoListScreen(
      userName = renderProps.username,
      todoTitles = titles,
      onToDoSelected = { context.actionSink.send(selectTodo(it)) },
      onBack = { context.actionSink.send(onBack()) }
    )

    return todoListScreen
  }

  private fun selectTodo(index: Int) = action {
    setOutput(SelectTodo(index))
  }

  private fun onBack() = action {
    setOutput(Back)
  }

  // private fun discardChanges() = action {
  //   state = state.copy(step = Step.List)
  // }
  //
  // private fun saveChanges(
  //   todoModel: TodoModel,
  //   index: Int
  // ) = action {
  //   state = state.copy(
  //     todos = state.todos.toMutableList().also{ it [index] = todoModel},
  //     step = Step.List
  //   )
  // }
}
