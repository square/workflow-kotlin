package com.squareup.sample.todo

/**
 * Core model object of the Todo app.
 */
data class TodoList(
  val title: String,
  val rows: List<TodoRow> = emptyList(),
  val id: Int = ++serial
) {
  private companion object {
    var serial = 0
  }
}

data class TodoRow(
  val text: String,
  val done: Boolean = false
)
