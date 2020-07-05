package com.squareup.workflow.internal

import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow2
import com.squareup.workflow.action
import com.squareup.workflow.internal.FooWorkflow2.Rendering
import com.squareup.workflow.internal.FooWorkflow2.State

/**
 * TODO write documentation
 */
class FooWorkflow2 : StatefulWorkflow2<String, State, String, Rendering>() {

  data class State(val foo: String)
  data class Rendering(
    val props: String,
    val foo: String,
    val onClick: () -> Unit
  )

  override fun initialState(
    props: String,
    snapshot: Snapshot?
  ): State = State("initial state (props=$props)")

  override fun render(
    props: String,
    state: State,
    context: RenderContext2
  ): Rendering = Rendering(
      props = props,
      foo = state.foo,
      onClick = {
        context.actionSink.send(action {
          this.state = this.state.copy(foo = state.foo + state.foo)
          setOutput("FOO!!")
        })
      }
  )

  override fun snapshotState(state: State): Snapshot? = null

  private inner class SpecialAction : WorkflowAction2() {
    override fun Updater2.apply() {
      state = state.copy(foo = state.foo + state.foo)
      setOutput("FOO!")
    }
  }
}