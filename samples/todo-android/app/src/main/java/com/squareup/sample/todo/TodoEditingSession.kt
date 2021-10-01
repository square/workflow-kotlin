package com.squareup.sample.todo

import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * An Android compatible editing session for a [TodoList]. Provides
 * a [TextController] for a list's [title][TodoList.title], and
 * for each of its [entries][TodoList.entries].
 *
 * Serves as the state of [TodoEditorWorkflow], and also as part of
 * its rendering type, [TodoEditorScreen]. The workflow tracks updates via
 * [TextController.onTextChanged], and view code can drive
 * [EditText][android.widget.EditText] instances with [TextController.control].
 *
 * Allowing view state and workflow state to bleed together like this is
 * not a great practice generally, but keeping up with live text editing
 * is…hard. So, for such use cases, using [TextController] in this style
 * _is_ recommended.
 */
@OptIn(WorkflowUiExperimentalApi::class)
data class TodoEditingSession(
  val id: Int,
  val title: TextController,
  val rows: List<RowEditingSession>
) {
  data class RowEditingSession(
    val textController: TextController = TextController(""),
    val checked: Boolean = false,
    val id: Int = ++serial,
  ) {
    private companion object {
      var serial = 0
    }
  }
}
