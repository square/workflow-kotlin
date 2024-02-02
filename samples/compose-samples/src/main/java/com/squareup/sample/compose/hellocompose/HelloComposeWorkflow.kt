package com.squareup.sample.compose.hellocompose

import com.squareup.sample.compose.hellocompose.HelloComposeWorkflow.State
import com.squareup.sample.compose.hellocompose.HelloComposeWorkflow.State.Goodbye
import com.squareup.sample.compose.hellocompose.HelloComposeWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse

object HelloComposeWorkflow : StatefulWorkflow<Unit, State, Nothing, HelloComposeScreen>() {
  enum class State {
    Hello,
    Goodbye;

    fun theOtherState(): State = when (this) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }

  private val helloAction = action {
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
    context: RenderContext
  ): HelloComposeScreen = HelloComposeScreen(
    message = renderState.name,
    onClick = { context.actionSink.send(helloAction) }
  )

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)
}
