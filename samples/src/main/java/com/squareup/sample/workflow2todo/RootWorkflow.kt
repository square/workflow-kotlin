package com.squareup.sample.workflow2todo

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow2.renderChild

@OptIn(ExperimentalStdlibApi::class)
@Composable fun RootWorkflow(): Backstack {
  println("OMG RootWorkflow()")

  val username = remember { mutableStateOf<String?>(null) }

  val screens = buildList {
    add(renderChild { WelcomeWorkflow(onLogin = { username.value = it }) })

    username.value?.let {
      add(renderChild {
        TodoListWorkflow(
            username = it,
            onLogout = { username.value = null }
        )
      })
    }
  }

  return Backstack(screens)
}
