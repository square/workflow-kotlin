package com.squareup.sample.todo.unmanagedstate

  import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.todo.TodoEditorOutput
import com.squareup.sample.todo.TodoEditorOutput.Done
import com.squareup.sample.todo.TodoEditorOutput.ListUpdated
import com.squareup.sample.todo.TodoList
import com.squareup.sample.todo.TodoListsAppState
import com.squareup.sample.todo.TodoListsAppState.EditingList
import com.squareup.sample.todo.TodoListsAppState.ShowingLists
import com.squareup.sample.todo.TodoListsScreen
import com.squareup.workflow.compose.ComposeWorkflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen

@WorkflowUiExperimentalApi
object TodoListsAppComposeWorkflow : ComposeWorkflow<Unit, Nothing, OverviewDetailScreen> {

  private val listsWorkflow = TodoListsComposeWorkflow()
  private val editorWorkflow = TodoEditorComposeWorkflow()

  private fun onListSelected(
    index: Int,
    state: TodoListsAppState
  ): TodoListsAppState {
    return EditingList(state.lists, index)
  }

  private fun onEditOutput(
    output: TodoEditorOutput,
    state: TodoListsAppState
  ): TodoListsAppState {
    return when (output) {
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
    output: (Nothing) -> Unit
  ): OverviewDetailScreen {
    var renderState: TodoListsAppState by remember {
      mutableStateOf(
        ShowingLists(
          listOf(
            TodoList("Groceries"),
            TodoList("Daily Chores"),
            TodoList("Reminders")
          )
        )
      )
    }

    val listOfLists: TodoListsScreen = listsWorkflow.render(renderState.lists) { index ->
      renderState = onListSelected(index, renderState)
    }

    return when (renderState) {
      // Nothing is selected. We rest in this state on a phone in portrait orientation.
      // In a overview detail layout, selectDefault can be called immediately, so that
      // the detail panel is never seen to be empty.
      is ShowingLists -> OverviewDetailScreen(
        overviewRendering = BackStackScreen(listOfLists),
        selectDefault = { renderState = onListSelected(0, renderState) }
      )

      // We are editing a list. Notice that we always render the overview pane -- the
      // workflow has no knowledge of whether the view side is running in a single
      // pane config or as an overview / detail split view.
      //
      // Also notice that we update the TodoListsScreen rendering that we got from the
      // TodoListsWorkflow child to reflect the current selection. The child workflow has no
      // notion of selection, and leaves that field set to the default value of -1.

      is EditingList -> editorWorkflow.render(
        renderState.lists[(renderState as EditingList).editingIndex],
        output = { renderState = onEditOutput(it, renderState) }
      ).let { editScreen ->
        OverviewDetailScreen(
          overviewRendering = BackStackScreen(
            listOfLists.copy(selection = (renderState as EditingList).editingIndex)
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
