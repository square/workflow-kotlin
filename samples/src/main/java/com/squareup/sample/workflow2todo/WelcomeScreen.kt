package com.squareup.sample.workflow2todo

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.workflow2.SelfRendering

data class WelcomeScreen(
  val username: String,
  val onUsernameChanged: (String) -> Unit,
  val onLogin: () -> Unit
) : SelfRendering {

  @Composable override fun render() {
    println("OMG WelcomeScreen.render()")

    Column {
      TextField(username, onValueChange = onUsernameChanged)
      Button(onClick = onLogin) {
        Text("Login")
      }
    }
  }
}

@Composable fun WelcomeWorkflow(onLogin: (String) -> Unit): WelcomeScreen {
  // Text("WelcomeWorkflow")

  var username by remember { mutableStateOf("") }

  println("OMG WelcomeWorkflow() username=$username")

  return WelcomeScreen(
      username = username,
      onUsernameChanged = {
        println("OMG WelcomeWorkflow got new username: $username -> $it")
        username = it
      },
      onLogin = { onLogin(username) }
  )
}
