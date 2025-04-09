package com.squareup.sample.compose.textinput

import com.squareup.sample.compose.textinput.TextInputWorkflow.Rendering
import com.squareup.sample.compose.textinput.TextInputWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.TextController

object TextInputWorkflow : StatefulWorkflow<Unit, State, Nothing, Rendering>() {

  data class State(
    val textA: TextController = TextController(),
    val textB: TextController = TextController(),
    val showingTextA: Boolean = true
  )

  data class Rendering(
    val textController: TextController,
    val onSwapText: () -> Unit
  ) : Screen

  private val swapText = action("swapText") {
    state = state.copy(showingTextA = !state.showingTextA)
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State()

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): Rendering = Rendering(
    textController = if (renderState.showingTextA) renderState.textA else renderState.textB,
    onSwapText = { context.actionSink.send(swapText) }
  )

  override fun snapshotState(state: State): Snapshot? = null
}
