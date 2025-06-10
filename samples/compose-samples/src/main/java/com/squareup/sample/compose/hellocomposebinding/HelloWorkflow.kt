package com.squareup.sample.compose.hellocomposebinding

import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.Rendering
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.State
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.State.Goodbye
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.Screen

object HelloWorkflow : StatefulWorkflow<Unit, State, Nothing, Rendering>() {
  enum class State {
    Hello,
    Goodbye;

    fun theOtherState(): State = when (this) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }

  data class Rendering(
    val message: String,
    val onClick: () -> Unit
  ) : Screen

  private val helloAction = action("hello") {
    state = state.theOtherState()
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.bytes?.parse { source -> if (source.readInt() == 1) Hello else Goodbye }
    ?: Hello

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext<Unit, State, Nothing>
  ): Rendering {
    return Rendering(
      message = renderState.name,
      onClick = { context.actionSink.send(helloAction) }
    )
  }

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)
}
