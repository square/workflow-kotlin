package com.squareup.sample.todo.managedstate

import androidx.compose.runtime.Composable
import com.squareup.sample.todo.TodoEditingSession
import com.squareup.sample.todo.TodoEditorOutput
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.sample.todo.TodoEditorScreen
import com.squareup.sample.todo.TodoList
import com.squareup.sample.todo.maintainEmptyLastRow
import com.squareup.sample.todo.toEditingSession
import com.squareup.sample.todo.toTodoList
import com.squareup.workflow.compose.StatefulComposeWorkflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
class TodoEditorComposeWorkflow :
  StatefulComposeWorkflow<TodoList, TodoEditingSession, TodoEditorOutput, TodoEditorScreen>() {

  override fun initialState(props: TodoList): TodoEditingSession = props.toEditingSession()

  @Composable override fun render(
    renderProps: TodoList,
    renderState: TodoEditingSession,
    context: RenderContext
  ): TodoEditorScreen {
    // Monitor the title and each row for text changes.
    renderState.title.onTextChanged { context.onTextChanged() }

    renderState.rows.forEach { row ->
      row.textController.onTextChanged { context.onTextChanged() }
    }

    return TodoEditorScreen(
      session = renderState,
      onCheckboxClicked = { index -> context.checkboxClicked(index) },
      onDeleteClicked = { index -> context.deleteClicked(index) },
      onGoBackClicked = { context.setOutput(Done) }
    )
  }

  private fun RenderContext.onTextChanged() {
    state = state.maintainEmptyLastRow()
    setOutput(ListUpdated(state.toTodoList()))
  }

  private fun RenderContext.checkboxClicked(index: Int) {
    state = state.copy(
      rows = state.rows.mapIndexed { i, row ->
        if (i == index) row.copy(checked = !row.checked) else row
      }
    ).maintainEmptyLastRow()
    setOutput(ListUpdated(state.toTodoList()))
  }

  private fun RenderContext.deleteClicked(index: Int) {
    state = state.copy(rows = state.rows.filterIndexed { i, _ -> i != index })
      .maintainEmptyLastRow()
    setOutput(ListUpdated(state.toTodoList()))
  }
}
