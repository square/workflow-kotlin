package workflow.tutorial.todolist

import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import workflow.tutorial.R
import workflow.tutorial.views.TodoListAdapter
import workflow.tutorial.views.databinding.TodoListViewBinding

data class ToDoListScreen(
  val userName: String,
  val todoTitles: List<String>,
  val onToDoSelected: (Int) -> Unit,
  val onBack: () -> Unit,
)

@OptIn(WorkflowUiExperimentalApi::class)
class ToDoListLayoutRunner(
  private val todoListBinding: TodoListViewBinding,
) : LayoutRunner<ToDoListScreen> {

  private val adapter = TodoListAdapter()

  init {
    todoListBinding.todoList.layoutManager = LinearLayoutManager(todoListBinding.root.context)
    todoListBinding.todoList.adapter = adapter
  }

  override fun showRendering(
    rendering: ToDoListScreen,
    viewEnvironment: ViewEnvironment
  ) {
    todoListBinding.root.backPressedHandler = rendering.onBack

    with(todoListBinding.todoListWelcome){
      text = resources.getString(R.string.todo_list_welcome, rendering.userName)
    }

    adapter.todoList = rendering.todoTitles
    adapter.onTodoSelected = rendering.onToDoSelected
    adapter.notifyDataSetChanged()
  }

  companion object : ViewFactory<ToDoListScreen> by bind(
    TodoListViewBinding::inflate, ::ToDoListLayoutRunner
  )
}
