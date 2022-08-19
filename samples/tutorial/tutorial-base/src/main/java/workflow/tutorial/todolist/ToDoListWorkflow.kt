package workflow.tutorial.todolist

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import workflow.tutorial.todolist.ToDoListWorkflow.State


object ToDoListWorkflow : StatefulWorkflow<Unit, State, Nothing, ToDoListScreen>() {

  data class State(val placeholder: String = "")

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State("initial")

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): ToDoListScreen {
    return ToDoListScreen(
      userName = "",
      todoTitles = emptyList(),
      onToDoSelected = {},
      onBack = {}
    )
  }

  override fun snapshotState(state: State): Snapshot? = null
}
