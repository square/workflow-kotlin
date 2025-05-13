package workflow.tutorial

import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.Output
import workflow.tutorial.TodoListWorkflow.Output.Back
import workflow.tutorial.TodoListWorkflow.Output.SelectTodo

object TodoListWorkflow : StatelessWorkflow<ListProps, Output, TodoListScreen>() {

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  sealed class Output {
    object Back : Output()
    data class SelectTodo(val index: Int) : Output()
  }

  override fun render(
    renderProps: ListProps,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderProps.todos.map { it.title }
    return TodoListScreen(
        username = renderProps.username,
        todoTitles = titles,
        onTodoSelected = { context.actionSink.send(selectTodo(it)) },
        onBack = { context.actionSink.send(onBack()) }
    )
  }

  private fun onBack() = action("onBack") {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun selectTodo(index: Int) = action("selectTodo") {
    // Tell our parent that a todo item was selected.
    setOutput(SelectTodo(index))
  }
}
