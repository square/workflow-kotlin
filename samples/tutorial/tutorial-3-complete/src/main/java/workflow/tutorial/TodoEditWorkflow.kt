package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.TextController
import workflow.tutorial.TodoEditWorkflow.Output
import workflow.tutorial.TodoEditWorkflow.Output.DiscardChanges
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoEditWorkflow.State
import workflow.tutorial.TodoListWorkflow.TodoModel

object TodoEditWorkflow : StatefulWorkflow<EditProps, State, Output, TodoEditScreen>() {

  /** @param initialTodo The model passed from our parent to be edited. */
  data class EditProps(
    val initialTodo: TodoModel
  )

  /**
   * In-flight edits to be applied to the [TodoModel] originally provided
   * by the parent workflow.
   */
  data class State(
    val editedTitle: TextController,
    val editedNote: TextController
  ) {
    /** Transform this edited [State] back to a [TodoModel]. */
    fun toModel(): TodoModel = TodoModel(editedTitle.textValue, editedNote.textValue)

    companion object {
      /** Create a [State] suitable for editing the given [model]. */
      fun forModel(model: TodoModel): State = State(
        editedTitle = TextController(model.title),
        editedNote = TextController(model.note)
      )
    }
  }

  sealed interface Output {
    object DiscardChanges : Output
    data class SaveChanges(val todo: TodoModel) : Output
  }

  override fun initialState(
    props: EditProps,
    snapshot: Snapshot?
  ): State = State.forModel(props.initialTodo)

  override fun render(
    renderProps: EditProps,
    renderState: State,
    context: RenderContext
  ): TodoEditScreen = TodoEditScreen(
    title = renderState.editedTitle,
    note = renderState.editedNote,
    onSavePressed = context.eventHandler("onSavePressed") {
      setOutput(SaveChanges(state.toModel()))
    },
    onBackPressed = context.eventHandler("onBackPressed") {
      setOutput(DiscardChanges)
    }
  )

  override fun snapshotState(state: State): Snapshot? = null
}
