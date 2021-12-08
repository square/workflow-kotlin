package com.squareup.sample.todo.managedstate

import androidx.compose.runtime.Composable
import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.todo.TodoEditorOutput
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.sample.todo.TodoList
import com.squareup.sample.todo.TodoListsAppState
import com.squareup.sample.todo.TodoListsAppState.EditingList
import com.squareup.sample.todo.TodoListsAppState.ShowingLists
import com.squareup.sample.todo.TodoListsScreen
import com.squareup.workflow.compose.StatefulComposeWorkflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen

@WorkflowUiExperimentalApi
object TodoListsAppComposeWorkflow :
  StatefulComposeWorkflow<Unit, TodoListsAppState, Nothing, OverviewDetailScreen>() {
  override fun initialState(
    props: Unit
  ): TodoListsAppState = ShowingLists(
    listOf(
      TodoList("Groceries"),
      TodoList("Daily Chores"),
      TodoList("Reminders")
    )
  )

  private val listsWorkflow = TodoListsComposeWorkflow()
  private val editorWorkflow = TodoEditorComposeWorkflow()

  private fun RenderContext.onListSelected(index: Int) {
    state = EditingList(state.lists, index)
  }

  private fun RenderContext.onEditOutput(output: TodoEditorOutput) {
    state = when (output) {
      is ListUpdated -> {
        val oldState = state as EditingList
        oldState.copy(
          lists = state.lists.updateRow(oldState.editingIndex, output.newList)
        )
      }
      Done -> ShowingLists(state.lists)
    }
  }

  @Composable override fun render(
    renderProps: Unit,
    renderState: TodoListsAppState,
    context: RenderContext
  ): OverviewDetailScreen {
    val listOfLists: TodoListsScreen = listsWorkflow.render(renderState.lists) { index ->
      context.onListSelected(index)
    }

    return when (renderState) {
      // Nothing is selected. We rest in this state on a phone in portrait orientation.
      // In a overview detail layout, selectDefault can be called immediately, so that
      // the detail panel is never seen to be empty.
      is ShowingLists -> OverviewDetailScreen(
        overviewRendering = BackStackScreen(listOfLists),
        selectDefault = { context.onListSelected(0) }
      )

      // We are editing a list. Notice that we always render the overview pane -- the
      // workflow has no knowledge of whether the view side is running in a single
      // pane config or as an overview / detail split view.
      //
      // Also notice that we update the TodoListsScreen rendering that we got from the
      // TodoListsWorkflow child to reflect the current selection. The child workflow has no
      // notion of selection, and leaves that field set to the default value of -1.

      is EditingList -> editorWorkflow.render(renderState.lists[renderState.editingIndex]) {
        context.onEditOutput(it)
      }
        .let { editScreen ->
          OverviewDetailScreen(
            overviewRendering = BackStackScreen(
              listOfLists.copy(selection = renderState.editingIndex)
            ),
            detailRendering = BackStackScreen(editScreen)
          )
        }
    }
  }
}

private fun <T> List<T>.updateRow(
  index: Int,
  newValue: T
): List<T> = withIndex().map { (i, value) ->
  if (i == index) newValue else value
}
