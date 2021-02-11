package com.squareup.sample.todo

import com.squareup.workflow1.StatelessWorkflow

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
