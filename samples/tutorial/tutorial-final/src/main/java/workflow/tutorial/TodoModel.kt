package workflow.tutorial

import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class TodoModel(
  val title: TextController,
  val note: TextController
) {
  constructor(
    title: String,
    note: String
  ) : this(TextController(title), TextController(note))
}
