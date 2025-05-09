package workflow.tutorial

import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.setTextChangedListener
import com.squareup.workflow1.ui.updateText
import workflow.tutorial.views.databinding.TodoEditViewBinding

class TodoEditLayoutRunner(
  private val binding: TodoEditViewBinding
) : LayoutRunner<TodoEditScreen> {

  override fun showRendering(
    rendering: TodoEditScreen,
    viewEnvironment: ViewEnvironment
  ) {
    binding.root.backPressedHandler = rendering.discardChanges
    binding.save.setOnClickListener { rendering.saveChanges() }
    binding.todoTitle.updateText(rendering.title)
    binding.todoTitle.setTextChangedListener { rendering.onTitleChanged(it.toString()) }
    binding.todoNote.updateText(rendering.note)
    binding.todoNote.setTextChangedListener { rendering.onNoteChanged(it.toString()) }
  }

  companion object : ViewFactory<TodoEditScreen> by bind(
      TodoEditViewBinding::inflate, ::TodoEditLayoutRunner
  )
}
