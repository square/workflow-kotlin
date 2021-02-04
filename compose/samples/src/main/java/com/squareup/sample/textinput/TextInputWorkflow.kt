/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sample.textinput

import com.squareup.sample.textinput.TextInputWorkflow.Rendering
import com.squareup.sample.textinput.TextInputWorkflow.State
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.action

object TextInputWorkflow : StatefulWorkflow<Unit, State, Nothing, Rendering>() {

  data class State(
    val textA: String = "",
    val textB: String = "",
    val showingTextA: Boolean = true
  )

  data class Rendering(
    val text: String,
    val onTextChanged: (String) -> Unit,
    val onSwapText: () -> Unit
  )

  private val swapText = action {
    nextState = nextState.copy(showingTextA = !nextState.showingTextA)
  }

  private fun changeText(text: String) = action {
    nextState = if (nextState.showingTextA) {
      nextState.copy(textA = text)
    } else {
      nextState.copy(textB = text)
    }
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State()

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext<State, Nothing>
  ): Rendering = Rendering(
    text = if (state.showingTextA) state.textA else state.textB,
    onTextChanged = { context.actionSink.send(changeText(it)) },
    onSwapText = { context.actionSink.send(swapText) }
  )

  override fun snapshotState(state: State): Snapshot = Snapshot.EMPTY
}
