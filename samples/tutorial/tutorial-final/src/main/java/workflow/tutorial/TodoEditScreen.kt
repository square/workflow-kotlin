package workflow.tutorial

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.control
import com.squareup.workflow1.ui.navigation.setBackHandler
import workflow.tutorial.views.databinding.TodoEditViewBinding

data class TodoEditScreen(
  /** The title of this todo item. */
  val title: TextController,
  /** The contents, or "note" of the todo. */
  val note: TextController,

  val onBackPressed: () -> Unit,
  val onSavePressed: () -> Unit
) : AndroidScreen<TodoEditScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(TodoEditViewBinding::inflate, ::todoEditScreenRunner)
}

private fun todoEditScreenRunner(
  binding: TodoEditViewBinding
) = ScreenViewRunner { screen: TodoEditScreen, _ ->
  binding.root.setBackHandler(screen.onBackPressed)
  binding.save.setOnClickListener { screen.onSavePressed() }

  screen.title.control(binding.todoTitle)
  screen.note.control(binding.todoNote)
}
