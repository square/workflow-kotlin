package com.squareup.sample.helloworkflowfragment

import com.squareup.sample.helloworkflowfragment.HelloWorkflow.State
import com.squareup.sample.helloworkflowfragment.HelloWorkflow.State.Goodbye
import com.squareup.sample.helloworkflowfragment.HelloWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse

object HelloWorkflow : StatefulWorkflow<Unit, State, Nothing, HelloRendering>() {
  enum class State {
    Hello,
    Goodbye
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.bytes?.parse { source -> if (source.readInt() == 1) Hello else Goodbye }
    ?: Hello

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): HelloRendering {
    return HelloRendering(
      message = state.name,
      onClick = { context.actionSink.send(helloAction) }
    )
  }

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)

  private val helloAction = action {
    state = when (state) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }
}
