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
    renderProps: Unit,
    renderState: State,
    context: RenderContext<Unit, State, Nothing>
  ): HelloRendering {
    return HelloRendering(
      message = renderState.name,
      onClick = { context.actionSink.send(helloAction) }
    )
  }

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)

  private val helloAction = action("hello") {
    state = when (state) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }
}
