package workflow.tutorial

import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.setBackHandler
import workflow.tutorial.views.TodoListAdapter
import workflow.tutorial.views.databinding.TodoListViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
class TodoListScreenRunner(
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
    todoListBinding.root.setBackHandler(rendering.onBackClick)
    todoListBinding.add.setOnClickListener { rendering.onAddClick() }

    with(todoListBinding.todoListWelcome) {
      text =
        resources.getString(workflow.tutorial.views.R.string.todo_list_welcome, rendering.username)
    }

    adapter.todoList = rendering.todoTitles
    adapter.onTodoSelected = rendering.onTodoSelected
    adapter.notifyDataSetChanged()
  }
}
