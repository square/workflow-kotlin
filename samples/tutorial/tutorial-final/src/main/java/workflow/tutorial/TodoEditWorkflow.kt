package workflow.tutorial

import androidx.annotation.VisibleForTesting
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.TextController
import workflow.tutorial.TodoEditWorkflow.Output
import workflow.tutorial.TodoEditWorkflow.Output.DiscardChanges
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoEditWorkflow.State

object TodoEditWorkflow : StatefulWorkflow<EditProps, State, Output, TodoEditScreen>() {

  data class EditProps(
    /** The "Todo" passed from our parent. */
    val initialTodo: TodoModel
  )

  data class State(
    /** The workflow's copy of the Todo item. Changes are local to this workflow. */
    val editedTitle: TextController,
    val editedNote: TextController
  ) {
    fun toModel(): TodoModel {
      return TodoModel(editedTitle.textValue, editedNote.textValue)
    }

    companion object {
      fun forModel(model: TodoModel): State {
        return State(
          editedTitle = TextController(model.title),
          editedNote = TextController(model.note)
        )
      }
    }
  }

  sealed class Output {
    object DiscardChanges : Output()
    data class SaveChanges(val todo: TodoModel) : Output()
  }

  override fun initialState(
    props: EditProps,
    snapshot: Snapshot?
  ): State = State.forModel(props.initialTodo)

  override fun onPropsChanged(
    old: EditProps,
    new: EditProps,
    state: State
  ): State {
    // The initialTodo from our parent changed. Update our internal copy so we are starting
    // from the same item. Note that onPropsChanged will only be called when old != new.
    //
    // The "correct" behavior depends on the business logic. Is it ok to delete whatever edits
    // were in progress if the state from the parent changes?
    return State.forModel(new.initialTodo)
  }

  override fun render(
    renderProps: EditProps,
    renderState: State,
    context: RenderContext
  ): TodoEditScreen {
    return TodoEditScreen(
      title = renderState.editedTitle,
      note = renderState.editedNote,
      onSavePressed = { context.actionSink.send(requestSave) },
      onBackPressed = { context.actionSink.send(requestDiscard) }
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  private val requestDiscard = action("requestDiscard") {
    setOutput(DiscardChanges)
  }

  @VisibleForTesting
  internal val requestSave = action("requestSave") {
    setOutput(SaveChanges(state.toModel()))
  }
}
