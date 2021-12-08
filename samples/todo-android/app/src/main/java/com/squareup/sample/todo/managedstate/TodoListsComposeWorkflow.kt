package com.squareup.sample.todo.managedstate

import androidx.compose.runtime.Composable
import com.squareup.sample.todo.TodoList
import com.squareup.sample.todo.TodoListsScreen
import com.squareup.workflow.compose.ComposeWorkflow

class TodoListsComposeWorkflow : ComposeWorkflow<List<TodoList>, Int, TodoListsScreen> {
  @Composable override fun render(
    props: List<TodoList>,
    output: (Int) -> Unit
  ): TodoListsScreen {
    return TodoListsScreen(
      lists = props,
      onRowClicked = output
    )
  }
}
