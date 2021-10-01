package com.squareup.sample.todo

import android.view.LayoutInflater
import android.widget.TextView
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Overview
import com.squareup.sample.todo.databinding.TodoListsLayoutBinding
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Rendering of the list of [TodoList]s.
 *
 * Note that these renderings are created by [TodoListsWorkflow], which is unaware of the
 * [selection], always leaving that field set to the default `-1` value.
 *
 * The entire concept of selection is owned by the parent [TodoListsAppWorkflow],
 * which may add that info to a copy of the child workflow's rendering.
 */
@OptIn(WorkflowUiExperimentalApi::class)
data class TodoListsScreen(
  val lists: List<TodoList>,
  val onRowClicked: (Int) -> Unit,
  val selection: Int = -1
) : AndroidViewRendering<TodoListsScreen> {
  override val viewFactory: ViewFactory<TodoListsScreen> =
    bind(TodoListsLayoutBinding::inflate) { rendering, viewEnvironment ->
      for ((index, list) in rendering.lists.withIndex()) {
        addRow(
          index,
          list,
          selectable = viewEnvironment[OverviewDetailConfig] == Overview,
          selected = index == rendering.selection &&
            viewEnvironment[OverviewDetailConfig] == Overview
        ) { rendering.onRowClicked(index) }
      }
      pruneDeadRowsFrom(rendering.lists.size)
    }
}

private fun TodoListsLayoutBinding.addRow(
  index: Int,
  list: TodoList,
  selectable: Boolean,
  selected: Boolean,
  onClick: () -> Unit
) {
  val row: TextView = if (index < todoListsContainer.childCount) {
    todoListsContainer.getChildAt(index)
  } else {
    val layout = when {
      selectable -> R.layout.todo_lists_selectable_row_layout
      else -> R.layout.todo_lists_unselectable_row_layout
    }
    LayoutInflater.from(root.context)
      .inflate(layout, todoListsContainer, false)
      .also { todoListsContainer.addView(it) }
  } as TextView

  row.isActivated = selected
  row.text = list.title
  row.setOnClickListener { onClick() }
}

private fun TodoListsLayoutBinding.pruneDeadRowsFrom(index: Int) {
  while (todoListsContainer.childCount > index) todoListsContainer.removeViewAt(index)
}
