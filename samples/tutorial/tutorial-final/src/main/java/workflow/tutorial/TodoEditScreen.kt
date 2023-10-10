package workflow.tutorial

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.views.databinding.TodoEditViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
data class TodoEditScreen(
  /** The title of this todo item. */
  val title: TextController,
  /** The contents, or "note" of the todo. */
  val note: TextController,

  val onBackPressed: () -> Unit,
  val onSavePressed: () -> Unit
) : AndroidScreen<TodoEditScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(TodoEditViewBinding::inflate, ::TodoEditScreenRunner)
}
