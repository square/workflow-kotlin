package workflow.tutorial

import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.navigation.setBackHandler
import workflow.tutorial.views.R
import workflow.tutorial.views.TodoListAdapter
import workflow.tutorial.views.databinding.TodoListViewBinding

data class TodoListScreen(
  val username: String,
  val todoTitles: List<String>,
  val onBackPressed: () -> Unit,
) : AndroidScreen<TodoListScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(TodoListViewBinding::inflate, ::todoListScreenRunner)
}

private fun todoListScreenRunner(
  todoListBinding: TodoListViewBinding
): ScreenViewRunner<TodoListScreen> {
  // This outer scope is run only once, right after the view is inflated.
  val adapter = TodoListAdapter()

  todoListBinding.todoList.layoutManager = LinearLayoutManager(todoListBinding.root.context)
  todoListBinding.todoList.adapter = adapter

  return ScreenViewRunner { screen: TodoListScreen, _ ->
    // This inner lambda is run on each update.
    todoListBinding.root.setBackHandler(screen.onBackPressed)

    with(todoListBinding.todoListWelcome) {
      text = resources.getString(R.string.todo_list_welcome, screen.username)
    }

    adapter.todoList = screen.todoTitles
    adapter.notifyDataSetChanged()
  }
}
