package com.squareup.sample.workflow2todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumnFor
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squareup.workflow2.SelfRendering

data class TodoModel(
  val title: String,
  val note: String
)

data class TodoListScreen(
  val username: String,
  val todos: List<TodoModel>,
  val onLogout: () -> Unit
) : SelfRendering {

  @Composable override fun render() {
    Column(Modifier.fillMaxSize()) {
      Text("Welcome $username")
      Text("What do you need to do")
      LazyColumnFor(items = todos) { todo ->
        Text(todo.title, Modifier.padding(16.dp))
      }
      Button(onClick = onLogout) {
        Text("Logout")
      }
    }
  }
}

@Composable fun TodoListWorkflow(
  username: String,
  onLogout: () -> Unit
): TodoListScreen {
  Text("TodoListWorkflow")

  var todos: List<TodoModel> by remember {
    mutableStateOf(
        listOf(
            TodoModel(title = "Take the cats for a walk.", note = "")
        )
    )
  }
  return TodoListScreen(username, todos, onLogout = onLogout)
}
