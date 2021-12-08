package com.squareup.sample.todo.unmanagedstate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.sample.todo.TodoEditingSession
import com.squareup.sample.todo.TodoEditorOutput
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.sample.todo.TodoEditorScreen
import com.squareup.sample.todo.TodoList
import com.squareup.sample.todo.maintainEmptyLastRow
import com.squareup.sample.todo.toEditingSession
import com.squareup.sample.todo.toTodoList
import com.squareup.workflow.compose.ComposeWorkflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
class TodoEditorComposeWorkflow : ComposeWorkflow<TodoList, TodoEditorOutput, TodoEditorScreen> {

  @Composable override fun render(
    renderProps: TodoList,
    output: (TodoEditorOutput) -> Unit
  ): TodoEditorScreen {
    var renderState: TodoEditingSession by remember { mutableStateOf(renderProps.toEditingSession()) }

    fun onTextChanged() {
      renderState = renderState.maintainEmptyLastRow()
      output(ListUpdated(renderState.toTodoList()))
    }

    // Monitor the title and each row for text changes.
    renderState.title.onTextChanged.collectAsState(null).value?.run { onTextChanged() }

    renderState.rows.forEach { row ->
      row.textController.onTextChanged.collectAsState(null).value?.run { onTextChanged() }
    }

    return TodoEditorScreen(
      session = renderState,
      onCheckboxClicked = { index ->
        renderState = checkboxClicked(index, renderState)
        output(ListUpdated(renderState.toTodoList()))
      },
      onDeleteClicked = { index ->
        renderState = deleteClicked(index, renderState)
        output(ListUpdated(renderState.toTodoList()))

      },
      onGoBackClicked = { output(Done) }
    )
  }

  private fun checkboxClicked(
    index: Int,
    currentState: TodoEditingSession
  ): TodoEditingSession {
    return currentState.copy(
      rows = currentState.rows.mapIndexed { i, row ->
        if (i == index) row.copy(checked = !row.checked) else row
      }
    ).maintainEmptyLastRow()
  }

  private fun deleteClicked(
    index: Int,
    currentState: TodoEditingSession
  ): TodoEditingSession {
    return currentState.copy(rows = currentState.rows.filterIndexed { i, _ -> i != index })
      .maintainEmptyLastRow()
  }
}
