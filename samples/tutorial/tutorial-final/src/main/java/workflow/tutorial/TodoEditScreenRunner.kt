package workflow.tutorial

import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.control
import com.squareup.workflow1.ui.setBackHandler
import workflow.tutorial.views.databinding.TodoEditViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
class TodoEditScreenRunner(
  private val binding: TodoEditViewBinding
) : ScreenViewRunner<TodoEditScreen> {

  override fun showRendering(
    rendering: TodoEditScreen,
    environment: ViewEnvironment
  ) {
    binding.root.setBackHandler(rendering.onBackPressed)
    binding.save.setOnClickListener { rendering.onSavePressed() }
    rendering.title.control(binding.todoTitle)
    rendering.note.control(binding.todoNote)
  }
}
