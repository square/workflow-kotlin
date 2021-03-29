package workflow.tutorial

import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.Output
import workflow.tutorial.TodoListWorkflow.Output.Back
import workflow.tutorial.TodoListWorkflow.Output.NewTodo
import workflow.tutorial.TodoListWorkflow.Output.SelectTodo

@OptIn(WorkflowUiExperimentalApi::class)
object TodoListWorkflow : StatelessWorkflow<ListProps, Output, TodoListScreen>() {

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  sealed class Output {
    object Back : Output()
    data class SelectTodo(val index: Int) : Output()
    object NewTodo : Output()
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

  private fun onBack() = action {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun selectTodo(index: Int) = action {
    // Tell our parent that a todo item was selected.
    setOutput(SelectTodo(index))
  }

  private fun new() = action {
    // Tell our parent a new todo item should be created.
    setOutput(NewTodo)
  }
}
