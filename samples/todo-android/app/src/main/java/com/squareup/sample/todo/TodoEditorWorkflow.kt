@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.todo

import com.squareup.sample.todo.TodoEditingSession.RowEditingSession
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowLocal
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

sealed class TodoEditorOutput {
  data class ListUpdated(val newList: TodoList) : TodoEditorOutput()
  object Done : TodoEditorOutput()
}

/**
 * Renders a given a [TodoList] as a [TodoEditorScreen]. Emits updated copies
 * of the list as output, via [ListUpdated]; or [Done] to indicate that editing
 * is complete.
 *
 * Note that a running instance ignores changes to its props, which works fine
 * in this sample but would be inadequate in real life. To change that,
 * add an implementation of [onPropsChanged].
 */
class TodoEditorWorkflow :
  StatefulWorkflow<TodoList, TodoEditingSession, TodoEditorOutput, TodoEditorScreen>() {
  override fun initialState(
    props: TodoList,
    snapshot: Snapshot?,
    workflowLocal: WorkflowLocal
  ): TodoEditingSession = props.toEditingSession()

  override fun snapshotState(state: TodoEditingSession): Snapshot? = null

  override fun render(
    renderProps: TodoList,
    renderState: TodoEditingSession,
    context: RenderContext
  ): TodoEditorScreen {
    // Monitor the title and each row for text changes.
    context.runningWorker(renderState.title.onTextChanged.asWorker(), "title") { textChanged }
    renderState.rows.forEach { row ->
      context.runningWorker(row.textController.onTextChanged.asWorker(), "${row.id}") {
        textChanged
      }
    }

    val sink = context.actionSink
    return TodoEditorScreen(
      session = renderState,
      onCheckboxClicked = { index -> sink.send(checkboxClicked(index)) },
      onDeleteClicked = { index -> sink.send(deleteClicked(index)) },
      onGoBackClicked = { sink.send(goBackClicked) }
    )
  }

  private val textChanged = action {
    state = state.maintainEmptyLastRow()
    setOutput(ListUpdated(state.toTodoList()))
  }

  private fun checkboxClicked(index: Int) = action {
    state = state.copy(
      rows = state.rows.mapIndexed { i, row ->
        if (i == index) row.copy(checked = !row.checked) else row
      }
    ).maintainEmptyLastRow()
    setOutput(ListUpdated(state.toTodoList()))
  }

  private fun deleteClicked(index: Int) = action {
    state = state.copy(rows = state.rows.filterIndexed { i, _ -> i != index })
      .maintainEmptyLastRow()
    setOutput(ListUpdated(state.toTodoList()))
  }

  private val goBackClicked = action {
    setOutput(Done)
  }
}

private fun TodoList.toEditingSession(): TodoEditingSession {
  return TodoEditingSession(
    id = id,
    title = TextController(title),
    rows = entries.map {
      RowEditingSession(TextController(it.text), it.done)
    }
  ).maintainEmptyLastRow()
}

private fun TodoEditingSession.toTodoList(): TodoList {
  return TodoList(
    title = title.textValue,
    entries = rows
      .mapIndexedNotNull { index, row ->
        if (index == rows.size - 1 && row.isEmpty()) {
          null
        } else {
          TodoEntry(row.textController.textValue, row.checked)
        }
      }
  )
}

private fun TodoEditingSession.maintainEmptyLastRow(): TodoEditingSession {
  return when {
    rows.isEmpty() -> copy(rows = listOf(RowEditingSession()))
    !rows.last().isEmpty() -> copy(rows = rows + RowEditingSession())
    rows.size > 1 && rows[rows.size - 2].isEmpty() ->
      copy(rows = rows.subList(0, rows.size - 1))
    else -> this
  }
}

private fun RowEditingSession.isEmpty(): Boolean {
  return textController.textValue.isEmpty() && !checked
}
