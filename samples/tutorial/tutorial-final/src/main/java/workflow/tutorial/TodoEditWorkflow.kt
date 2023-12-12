package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.TodoEditWorkflow.Output
import workflow.tutorial.TodoEditWorkflow.Output.DiscardChanges
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoEditWorkflow.State

@OptIn(WorkflowUiExperimentalApi::class)
object TodoEditWorkflow : StatefulWorkflow<EditProps, State, Output, TodoEditScreen>() {

  data class EditProps(
    /** The "Todo" passed from our parent. */
    val initialTodo: TodoModel
  )

  data class State(
    /** The workflow's copy of the Todo item. Changes are local to this workflow. */
    val todo: TodoModel
  )

  sealed class Output {
    object DiscardChanges : Output()
    data class SaveChanges(val todo: TodoModel) : Output()
  }

  override fun initialState(
    props: EditProps,
    snapshot: Snapshot?
  ): State = State(props.initialTodo)

  override fun onPropsChanged(
    old: EditProps,
    new: EditProps,
    state: State
  ): State {
    // The `Todo` from our parent changed. Update our internal copy so we are starting from the same
    // item. The "correct" behavior depends on the business logic - would we only want to update if
    // the users hasn't changed the todo from the initial one? Or is it ok to delete whatever edits
    // were in progress if the state from the parent changes?
    if (old.initialTodo != new.initialTodo) {
      return state.copy(todo = new.initialTodo)
    }
    return state
  }

  override fun render(
    renderProps: EditProps,
    renderState: State,
    context: RenderContext
  ): TodoEditScreen {
    return TodoEditScreen(
      title = renderState.todo.title,
      note = renderState.todo.note,
      onSavePressed = { context.actionSink.send(requestSave) },
      onBackPressed = { context.actionSink.send(requestDiscard) }
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  private val requestDiscard = action {
    setOutput(DiscardChanges)
  }

  internal val requestSave = action {
    setOutput(SaveChanges(state.todo))
  }
}
