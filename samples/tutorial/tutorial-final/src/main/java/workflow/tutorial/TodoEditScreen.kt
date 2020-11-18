package workflow.tutorial

data class TodoEditScreen(
  /** The title of this todo item. */
  val title: String,
  /** The contents, or "note" of the todo. */
  val note: String,

  /** Callbacks for when the title or note changes. */
  val onTitleChanged: (String) -> Unit,
  val onNoteChanged: (String) -> Unit,

  val discardChanges: () -> Unit,
  val saveChanges: () -> Unit
)
