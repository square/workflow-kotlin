package com.squareup.sample.helloworkflow

import com.squareup.sample.helloworkflow.HelloWorkflow.State
import com.squareup.sample.helloworkflow.HelloWorkflow.State.Goodbye
import com.squareup.sample.helloworkflow.HelloWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.parse
import com.squareup.workflow1.runningWorker
import kotlinx.coroutines.flow.flowOf

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

  var firstRender = true

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): HelloRendering {
    if (firstRender) {
      firstRender = false
      context.runningWorker(flowOf(Unit).asWorker()) { WorkflowAction.noAction() }
    } else {
      for (i in 1..100) {
        context.runningWorker(flowOf(Unit).asWorker(), "$i") { WorkflowAction.noAction() }
      }
    }

    return HelloRendering(
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
