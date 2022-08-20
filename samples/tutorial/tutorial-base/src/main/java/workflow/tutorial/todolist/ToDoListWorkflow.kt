package workflow.tutorial.todolist

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import workflow.tutorial.todolist.ToDoListWorkflow.Back
import workflow.tutorial.todolist.ToDoListWorkflow.ListProps
import workflow.tutorial.todolist.ToDoListWorkflow.State

object ToDoListWorkflow : StatefulWorkflow<ListProps, State, Back, ToDoListScreen>() {

  object Back

  data class ListProps(val username: String)

  data class ToDoModel(
    val title: String,
    val note: String
  )

  data class State(
    val todos: List<ToDoModel>
  )

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ): State = State(
    listOf(
      ToDoModel(
        title = "${props.username} -- Take the cat for a walk",
        note = "Cats really need their outside sunshine time. Don't forget to walk " +
          "Charlie. Hamilton is less excited about the prospect."
      )
    )
  )

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): ToDoListScreen {
    val titles = renderState.todos.map { it.title }

    return ToDoListScreen(
      userName = "",
      todoTitles = titles,
      onToDoSelected = {},
      onBack = {
        context.actionSink.send(onBack())
      }
    )
  }

  private fun onBack(): WorkflowAction<ListProps, State, Back> = action {
    setOutput(Back)
  }

  override fun snapshotState(state: State): Snapshot? = null
}
