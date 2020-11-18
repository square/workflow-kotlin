package workflow.tutorial

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.TodoListWorkflow.ListProps
import workflow.tutorial.TodoListWorkflow.Output
import workflow.tutorial.TodoListWorkflow.Output.Back
import workflow.tutorial.TodoListWorkflow.Output.NewTodo
import workflow.tutorial.TodoListWorkflow.Output.SelectTodo

@OptIn(WorkflowUiExperimentalApi::class)
object TodoListWorkflow : StatefulWorkflow<ListProps, Unit, Output, TodoListScreen>() {

  data class ListProps(
    val name: String,
    val todos: List<TodoModel>
  )

  sealed class Output {
    object Back : Output()
    data class SelectTodo(val index: Int) : Output()
    object NewTodo : Output()
  }

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ) = Unit

  override fun render(
    props: ListProps,
    state: Unit,
    context: RenderContext
  ): TodoListScreen {
    val titles = props.todos.map { it.title }
    return TodoListScreen(
        name = props.name,
        todoTitles = titles,
        onTodoSelected = { context.actionSink.send(selectTodo(it)) },
        onBack = { context.actionSink.send(onBack()) }
    )
  }

  override fun snapshotState(state: Unit): Snapshot? = null

  private fun onBack() = action {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun selectTodo(index: Int) = action {
    // Tell our parent that a todo item was selected.
    setOutput(SelectTodo(index))
  }

  private fun new() = action {
    // Tell our parent a new todo item should be created.
    setOutput(NewTodo)
  }

//  private fun discardChanges() = action {
//    // When a discard action is received, return to the list.
//    state = state.copy(step = Step.List)
//  }
//
//  private fun saveChanges(
//    todo: TodoModel,
//    index: Int
//  ) = action {
//    // When changes are saved, update the state of that todo item and return to the list.
//    state = state.copy(
//        todos = state.todos.toMutableList().also { it[index] = todo },
//        step = Step.List
//    )
//  }
}
