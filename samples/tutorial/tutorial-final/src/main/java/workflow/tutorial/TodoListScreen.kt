package workflow.tutorial

import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.navigation.setBackHandler
import workflow.tutorial.views.TodoListAdapter
import workflow.tutorial.views.databinding.TodoListViewBinding

/**
 * This should contain all data to display in the UI.
 *
 * It should also contain callbacks for any UI events, for example:
 * `val onButtonTapped: () -> Unit`.
 */
data class TodoListScreen(
  val username: String,
  val todoTitles: List<String>,
  val onRowPressed: (Int) -> Unit,
  val onBackPressed: () -> Unit,
  val onAddPressed: () -> Unit
) : AndroidScreen<TodoListScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(TodoListViewBinding::inflate, ::TodoListScreenRunner)
}

private class TodoListScreenRunner(
  private val todoListBinding: TodoListViewBinding
) : ScreenViewRunner<TodoListScreen> {

  private val adapter = TodoListAdapter()

  init {
    todoListBinding.todoList.layoutManager = LinearLayoutManager(todoListBinding.root.context)
    todoListBinding.todoList.adapter = adapter
  }

  override fun showRendering(
    rendering: TodoListScreen,
    environment: ViewEnvironment
  ) {
    todoListBinding.root.setBackHandler(rendering.onBackPressed)
    todoListBinding.add.setOnClickListener { rendering.onAddPressed() }

    with(todoListBinding.todoListWelcome) {
      text =
        resources.getString(workflow.tutorial.views.R.string.todo_list_welcome, rendering.username)
    }

    adapter.todoList = rendering.todoTitles
    adapter.onTodoSelected = rendering.onRowPressed
    adapter.notifyDataSetChanged()
  }
}
