package com.squareup.sample.compose.hellocompose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.sample.compose.hellocompose.HelloComposeWorkflow.State.Hello
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.WorkflowExperimentalApi

object HelloComposeWorkflow : StatelessWorkflow<Unit, Nothing, HelloComposeScreen>() {
  enum class State {
    Hello,
    Goodbye;

    fun theOtherState(): State = when (this) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }

  @OptIn(WorkflowExperimentalApi::class)
  override fun render(
    renderProps: Unit,
    context: RenderContext
  ): HelloComposeScreen = context.renderComposable {
    var state by remember { mutableStateOf(Hello) }
    HelloComposeScreen(
      message = state.name,
      onClick = { state = state.theOtherState() }
    )
  }
}
