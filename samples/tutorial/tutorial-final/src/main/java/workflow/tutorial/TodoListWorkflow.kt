package workflow.tutorial

import com.squareup.workflow1.StatelessWorkflow
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.Output
import workflow.tutorial.TodoListWorkflow.Output.AddPressed
import workflow.tutorial.TodoListWorkflow.Output.BackPressed
import workflow.tutorial.TodoListWorkflow.Output.TodoSelected

object TodoListWorkflow : StatelessWorkflow<ListProps, Output, TodoListScreen>() {

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  sealed interface Output {
    object BackPressed : Output
    data class TodoSelected(val index: Int) : Output
    object AddPressed : Output
  }

  override fun render(
    renderProps: ListProps,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderProps.todos.map { it.title }
    return TodoListScreen(
      username = renderProps.username,
      todoTitles = titles,
      onBackPressed = context.eventHandler("onBackPressed") { setOutput(BackPressed) },
      onRowPressed = context.eventHandler("onRowPressed") { index ->
        // Tell our parent that a todo item was selected.
        setOutput(TodoSelected(index))
      },
      onAddPressed = context.eventHandler("onAddPressed") { setOutput(AddPressed) }
    )
  }
}
