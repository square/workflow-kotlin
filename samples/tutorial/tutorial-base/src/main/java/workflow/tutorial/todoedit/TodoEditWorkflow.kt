package workflow.tutorial.todoedit

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import workflow.tutorial.todoedit.TodoEditWorkflow.EditProps
import workflow.tutorial.todoedit.TodoEditWorkflow.Output
import workflow.tutorial.todoedit.TodoEditWorkflow.Output.Discard
import workflow.tutorial.todoedit.TodoEditWorkflow.Output.Save
import workflow.tutorial.todoedit.TodoEditWorkflow.State

import workflow.tutorial.todolist.ToDoListWorkflow.TodoModel

object TodoEditWorkflow : StatefulWorkflow<EditProps, State, Output, TodoEditScreen>() {

  data class EditProps(
    val initialTodo: TodoModel
  )

  data class State(
    val todo: TodoModel
  )

  sealed class Output {
    object Discard : Output()
    data class Save(val todo: TodoModel) : Output()
  }

  override fun initialState(
    props: EditProps,
    snapshot: Snapshot?
  ): State = State(todo = props.initialTodo)

  override fun onPropsChanged(
    old: EditProps,
    new: EditProps,
    state: State
  ): State {
    if (old.initialTodo != new.initialTodo) {
      return state.copy(todo = new.initialTodo)
    }

    return state
  }

  private fun onTitleChanged(title: String) = action {
    state = state.withTitle(title)
  }

  private fun onNoteChanged(note: String) = action {
    state = state.withNote(note)
  }

  private fun onDiscard() = action {
    setOutput(Discard)
  }

  private fun onSave() = action {
    setOutput(Save(state.todo))
  }

  override fun render(
    renderProps: EditProps,
    renderState: State,
    context: RenderContext
  ): TodoEditScreen {
    return TodoEditScreen(
      title = renderState.todo.title,
      note = renderState.todo.note,
      onTitleChanged = {
        context.actionSink.send(onTitleChanged(it))
      },
      onNoteChanged = {
        context.actionSink.send(onNoteChanged(it))
      },
      discardChanges = { context.actionSink.send(onDiscard()) },
      saveChanges = { context.actionSink.send(onSave()) }
    )
  }

  override fun snapshotState(state: State): Snapshot? = Snapshot.write {
    TODO("Save state")
  }

  private fun State.withTitle(title: String) = copy(todo = todo.copy(title = title))
  private fun State.withNote(note: String) = copy(todo = todo.copy(note = note))
}
