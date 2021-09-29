package com.squareup.sample.todo

import com.squareup.sample.todo.TodoAction.GoBackClicked
import com.squareup.sample.todo.TodoAction.ListAction.DeleteClicked
import com.squareup.sample.todo.TodoAction.ListAction.DoneClicked
import com.squareup.sample.todo.TodoAction.ListAction.TextChanged
import com.squareup.sample.todo.TodoAction.ListAction.TitleChanged
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.WorkflowAction

sealed class TodoEditorOutput {
  data class ListUpdated(val newList: TodoList) : TodoEditorOutput()
  object Done : TodoEditorOutput()
}

/**
 * Renders a given a [TodoList] as a [TodoEditorScreen]. Emits updated copies
 * of the list as output, via [ListUpdated]; or [Done] to indicate that editing
 * is complete.
 */
class TodoEditorWorkflow : StatelessWorkflow<TodoList, TodoEditorOutput, TodoEditorScreen>() {

  override fun render(
    renderProps: TodoList,
    context: RenderContext
  ): TodoEditorScreen {
    val sink = context.actionSink

    return TodoEditorScreen(
      renderProps.copy(rows = renderProps.rows + TodoRow("")),
      onTitleChanged = { sink.send(TitleChanged(renderProps, it)) },
      onDoneClicked = { sink.send(DoneClicked(renderProps, it)) },
      onTextChanged = { index, newText -> sink.send(TextChanged(renderProps, index, newText)) },
      onDeleteClicked = { sink.send(DeleteClicked(renderProps, it)) },
      onGoBackClicked = { sink.send(GoBackClicked) }
    )
  }
}

private sealed class TodoAction : WorkflowAction<TodoList, Nothing, TodoEditorOutput>() {
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
