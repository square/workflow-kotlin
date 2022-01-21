package com.squareup.sample.todo

import com.squareup.workflow1.StatelessWorkflow

/**
 * Renders a given list of [TodoList]s as a [TodoListsScreen],
 * and emits the index of clicked entries as output.
 *
 * This workflow is unaware of selection, so renderings always have
 * [TodoListsScreen.selection] set to the default `-1` value. Parents
 * can modify it as needed.
 */
class TodoListsWorkflow : StatelessWorkflow<List<TodoList>, Int, TodoListsScreen>() {
  override fun render(
    renderProps: List<TodoList>,
    context: RenderContext
  ): TodoListsScreen {
    return TodoListsScreen(
      lists = renderProps,
      onRowClicked = context.eventHandler { index -> setOutput(index) }
    )
  }
}
