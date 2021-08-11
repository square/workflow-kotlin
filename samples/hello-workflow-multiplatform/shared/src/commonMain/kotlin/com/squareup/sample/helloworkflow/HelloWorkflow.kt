package com.squareup.sample.helloworkflow

import com.squareup.sample.helloworkflow.HelloWorkflow.State
import com.squareup.sample.helloworkflow.HelloWorkflow.State.Goodbye
import com.squareup.sample.helloworkflow.HelloWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse

class HelloWorkflow<T : HelloRendering>(
  private val renderingFactory: HelloRenderingFactory<T>
) : StatefulWorkflow<Unit, State, Nothing, T>() {

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
    context: RenderContext
  ): T {
    return renderingFactory.createRendering(
      message = renderState.name,
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

fun interface HelloRenderingFactory<T : HelloRendering> {
  fun createRendering(message: String, onClick: () -> Unit): T
}

interface HelloRendering {
  val message: String
  val onClick: () -> Unit
}
