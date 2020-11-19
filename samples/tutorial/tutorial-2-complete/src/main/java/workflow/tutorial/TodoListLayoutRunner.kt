package workflow.tutorial

import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import workflow.tutorial.views.TodoListAdapter
import workflow.tutorial.views.databinding.TodoListViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
class TodoListLayoutRunner(
  private val todoListBinding: TodoListViewBinding
) : LayoutRunner<TodoListScreen> {

  private val adapter = TodoListAdapter()

  init {
    todoListBinding.todoList.layoutManager = LinearLayoutManager(todoListBinding.root.context)
    todoListBinding.todoList.adapter = adapter
  }

  override fun showRendering(
    rendering: TodoListScreen,
    viewEnvironment: ViewEnvironment
  ) {
    todoListBinding.root.backPressedHandler = rendering.onBack

    with(todoListBinding.todoListWelcome) {
      text = resources.getString(R.string.todo_list_welcome, rendering.username)
    }

    adapter.todoList = rendering.todoTitles
    adapter.notifyDataSetChanged()
  }

  companion object : ViewFactory<TodoListScreen> by bind(
      TodoListViewBinding::inflate, ::TodoListLayoutRunner
  )
}
