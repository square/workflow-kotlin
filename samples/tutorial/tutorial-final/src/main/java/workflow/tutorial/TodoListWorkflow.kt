package workflow.tutorial

import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.Output
import workflow.tutorial.TodoListWorkflow.Output.BackPressed
import workflow.tutorial.TodoListWorkflow.Output.AddPressed
import workflow.tutorial.TodoListWorkflow.Output.TodoSelected

@OptIn(WorkflowUiExperimentalApi::class)
object TodoListWorkflow : StatelessWorkflow<ListProps, Output, TodoListScreen>() {

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  sealed class Output {
    object BackPressed : Output()
    data class TodoSelected(val index: Int) : Output()
    object AddPressed : Output()
  }

  override fun render(
    renderProps: ListProps,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderProps.todos.map { it.title }
    return TodoListScreen(
      username = renderProps.username,
      todoTitles = titles.map { it.textValue },
      onRowPressed = { context.actionSink.send(reportSelection(it)) },
      onBackPressed = { context.actionSink.send(reportBackPress) },
      onAddPressed = { context.actionSink.send(reportAddPress) }
    )
  }

  private val reportBackPress = action {
    // When an onBack action is received, emit a Back output.
    setOutput(BackPressed)
  }

  private fun reportSelection(index: Int) = action {
    // Tell our parent that a todo item was selected.
    setOutput(TodoSelected(index))
  }

  private val reportAddPress = action {
    // Tell our parent a new todo item should be created.
    setOutput(AddPressed)
  }
}
