package com.squareup.sample.mainactivity

import android.view.LayoutInflater
import android.widget.TextView
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Overview
import com.squareup.sample.todo.R
import com.squareup.sample.todo.TodoList
import com.squareup.sample.todo.TodoListsScreen
import com.squareup.sample.todo.databinding.TodoListsLayoutBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory

@OptIn(WorkflowUiExperimentalApi::class)
internal val TodoListsViewFactory: ViewFactory<TodoListsScreen> =
  LayoutRunner.bind(TodoListsLayoutBinding::inflate) { rendering, viewEnvironment ->
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
