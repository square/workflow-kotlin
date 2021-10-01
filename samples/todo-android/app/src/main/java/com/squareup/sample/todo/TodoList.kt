package com.squareup.sample.todo

/**
 * Core model object of the Todo app.
 */
data class TodoList(
  val title: String,
  val entries: List<TodoEntry> = emptyList(),
  val id: Int = ++serial
) {
  private companion object {
    var serial = 0
  }
}

data class TodoEntry(
  val text: String,
  val done: Boolean = false
)
