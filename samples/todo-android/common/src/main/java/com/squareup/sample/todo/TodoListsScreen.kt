package com.squareup.sample.todo

/**
 * Rendering of the list of [TodoList]s.
 *
 * Note that these renderings are created by [TodoListsWorkflow], which is unaware of the
 * [selection], always leaving that field set to the default `-1` value.
 *
 * The entire concept of selection is owned by the parent [TodoListsAppWorkflow],
 * which may add that info to a copy of the child workflow's rendering.
 */
data class TodoListsScreen(
  val lists: List<TodoList>,
  val onRowClicked: (Int) -> Unit,
  val selection: Int = -1
)
