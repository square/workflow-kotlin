package com.squareup.sample.hellocomposeworkflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.sample.hellocomposeworkflow.HelloComposeWorkflow.State.Goodbye
import com.squareup.sample.hellocomposeworkflow.HelloComposeWorkflow.State.Hello
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.ComposeWorkflow

@OptIn(WorkflowExperimentalApi::class)
object HelloComposeWorkflow : ComposeWorkflow<Unit, Nothing, HelloRendering>() {
  enum class State {
    Hello,
    Goodbye
  }

  @Composable
  override fun produceRendering(
    props: Unit,
    emitOutput: (Nothing) -> Unit
  ): HelloRendering {
    var state by remember { mutableStateOf(Hello) }
    println("OMG recomposing state=$state")
    return HelloRendering(
      message = state.name,
      onClick = {
        println("OMG onClick! state=$state")
        state = when (state) {
          Hello -> Goodbye
          Goodbye -> Hello
        }
      }
    )
  }
}
