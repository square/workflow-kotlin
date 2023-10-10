package workflow.tutorial

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.views.databinding.TodoListViewBinding

/**
 * This should contain all data to display in the UI.
 *
 * It should also contain callbacks for any UI events, for example:
 * `val onButtonTapped: () -> Unit`.
 */
@OptIn(WorkflowUiExperimentalApi::class)
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
