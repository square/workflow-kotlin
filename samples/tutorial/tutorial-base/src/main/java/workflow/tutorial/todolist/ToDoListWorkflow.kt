package workflow.tutorial.todolist

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import workflow.tutorial.todoedit.TodoEditWorkflow
import workflow.tutorial.todoedit.TodoEditWorkflow.EditProps
import workflow.tutorial.todoedit.TodoEditWorkflow.Output.Discard
import workflow.tutorial.todoedit.TodoEditWorkflow.Output.Save
import workflow.tutorial.todolist.ToDoListWorkflow.Back
import workflow.tutorial.todolist.ToDoListWorkflow.ListProps
import workflow.tutorial.todolist.ToDoListWorkflow.State
import workflow.tutorial.todolist.ToDoListWorkflow.State.Step
import workflow.tutorial.todolist.ToDoListWorkflow.State.Step.Edit

object ToDoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Any>>() {

  object Back

  data class ListProps(val username: String)

  data class TodoModel(
    val title: String,
    val note: String
  )

  data class State(
    val todos: List<TodoModel>,
    val step: Step
  ) {
    sealed class Step {
      object List : Step()
      data class Edit(val index: Int) : Step()
    }
  }

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ): State = State(
    listOf(
      TodoModel(
        title = "${props.username} -- Take the cat for a walk",
        note = "Cats really need their outside sunshine time. Don't forget to walk " +
          "Charlie. Hamilton is less excited about the prospect."
      )
    ),
    step = Step.List
  )

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): List<Any> {
    val titles = renderState.todos.map { it.title }

    val todoListScreen = ToDoListScreen(
      userName = renderProps.username,
      todoTitles =  titles,
      onToDoSelected =  { context.actionSink.send(selectTodo(it))},
      onBack = {context.actionSink.send(onBack())}
    )

    return when(val step = renderState.step){
      is Edit -> {
        val todoEditScreen= context.renderChild(
          child = TodoEditWorkflow,
          props = EditProps(initialTodo = renderState.todos[step.index])){output ->
          when(output){
            Discard -> discardChanges()
            is Save -> saveChanges(output.todo, step.index)
          }
        }
        return listOf(todoListScreen, todoEditScreen)
      }
      Step.List -> listOf(todoListScreen)
    }

    // return listOf(ToDoListScreen(
    //   userName = "",
    //   todoTitles = titles,
    //   onToDoSelected = {
    //     context.actionSink.send(selectTodo(it))
    //   },
    //   onBack = {
    //     context.actionSink.send(onBack())
    //   }
    // ))
  }

  private fun selectTodo(index: Int) = action {
    state = state.copy(step = Step.Edit(index))
  }

  private fun onBack(): WorkflowAction<ListProps, State, Back> = action {
    setOutput(Back)
  }

  private fun discardChanges() = action {
    state = state.copy(step = Step.List)
  }

  private fun saveChanges(
    todoModel: TodoModel,
    index: Int
  ) = action {
    state = state.copy(
      todos = state.todos.toMutableList().also{ it [index] = todoModel},
      step = Step.List
    )
  }

  override fun snapshotState(state: State): Snapshot? = null
}
