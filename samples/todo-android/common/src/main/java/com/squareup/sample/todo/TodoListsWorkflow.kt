package com.squareup.sample.todo

import com.squareup.workflow1.StatelessWorkflow

class TodoListsWorkflow : StatelessWorkflow<List<TodoList>, Int, TodoListsScreen>() {
  override fun render(
    props: List<TodoList>,
    context: RenderContext
  ): TodoListsScreen {
    return TodoListsScreen(
        lists = props,
        onRowClicked = context.eventHandler { index -> setOutput(index) }
    )
  }
}
