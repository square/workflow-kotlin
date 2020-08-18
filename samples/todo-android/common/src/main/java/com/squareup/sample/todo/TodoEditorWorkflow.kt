package com.squareup.sample.todo

import com.squareup.sample.todo.TodoAction.GoBackClicked
import com.squareup.sample.todo.TodoAction.ListAction.DeleteClicked
import com.squareup.sample.todo.TodoAction.ListAction.DoneClicked
import com.squareup.sample.todo.TodoAction.ListAction.TextChanged
import com.squareup.sample.todo.TodoAction.ListAction.TitleChanged
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.workflow1.Sink
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.WorkflowAction

data class TodoList(
  val title: String,
  val rows: List<TodoRow> = emptyList()
)

data class TodoRow(
  val text: String,
  val done: Boolean = false
)

sealed class TodoAction : WorkflowAction<TodoList, Nothing, TodoEditorOutput>() {
  object GoBackClicked : TodoAction()

  sealed class ListAction : TodoAction() {
    abstract val list: TodoList

    class TitleChanged(
      override val list: TodoList,
      val newTitle: String
    ) : ListAction()

    class DoneClicked(
      override val list: TodoList,
      val index: Int
    ) : ListAction()

    class TextChanged(
      override val list: TodoList,
      val index: Int,
      val newText: String
    ) : ListAction()

    class DeleteClicked(
      override val list: TodoList,
      val index: Int
    ) : ListAction()
  }

  override fun Updater.apply() {
    when (this@TodoAction) {
      is GoBackClicked -> Done
      is TitleChanged -> ListUpdated(list.copy(title = newTitle))
      is DoneClicked -> ListUpdated(list.updateRow(index) { copy(done = !done) })
      is TextChanged -> ListUpdated(list.updateRow(index) { copy(text = newText) })
      is DeleteClicked -> ListUpdated(list.removeRow(index))
    }.let { setOutput(it) }
  }
}

data class TodoRendering(
  val list: TodoList,
  val onTitleChanged: (title: String) -> Unit,
  val onDoneClicked: (index: Int) -> Unit,
  val onTextChanged: (index: Int, text: String) -> Unit,
  val onDeleteClicked: (index: Int) -> Unit,
  val onGoBackClicked: () -> Unit
)

sealed class TodoEditorOutput {
  data class ListUpdated(val newList: TodoList) : TodoEditorOutput()
  object Done : TodoEditorOutput()
}

class TodoEditorWorkflow : StatelessWorkflow<TodoList, TodoEditorOutput, TodoRendering>() {

  override fun render(
    props: TodoList,
    context: RenderContext
  ): TodoRendering {
    // Make event handling idempotent until https://github.com/square/workflow/issues/541 is fixed.
    var eventFired = false
    val sink = Sink<TodoAction> {
      if (eventFired) return@Sink
      eventFired = true
      context.actionSink.send(it)
    }

    return TodoRendering(
        props.copy(rows = props.rows + TodoRow("")),
        onTitleChanged = { sink.send(TitleChanged(props, it)) },
        onDoneClicked = { sink.send(DoneClicked(props, it)) },
        onTextChanged = { index, newText -> sink.send(TextChanged(props, index, newText)) },
        onDeleteClicked = { sink.send(DeleteClicked(props, it)) },
        onGoBackClicked = { sink.send(GoBackClicked) }
    )
  }
}

private fun TodoList.updateRow(
  index: Int,
  block: TodoRow.() -> TodoRow
) = copy(rows = if (index == rows.size) {
  rows + TodoRow("").block()
} else {
  rows.withIndex()
      .map { (i, value) ->
        if (i == index) value.block() else value
      }
})

private fun TodoList.removeRow(index: Int) = copy(rows = rows.filterIndexed { i, _ -> i != index })
