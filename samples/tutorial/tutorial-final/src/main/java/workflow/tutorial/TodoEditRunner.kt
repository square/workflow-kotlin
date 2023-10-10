package workflow.tutorial

import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.control
import com.squareup.workflow1.ui.setBackHandler
import workflow.tutorial.views.databinding.TodoEditViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
class TodoEditRunner(
  private val binding: TodoEditViewBinding
) : ScreenViewRunner<TodoEditScreen> {

  override fun showRendering(
    rendering: TodoEditScreen,
    environment: ViewEnvironment
  ) {
    binding.root.setBackHandler(rendering.onBackClick)
    binding.save.setOnClickListener { rendering.onSaveClick() }
    rendering.title.control(binding.todoTitle)
    rendering.note.control(binding.todoNote)
  }
}
