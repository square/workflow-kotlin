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
package com.squareup.sample.hellocomposerendering

import com.squareup.sample.hellocomposerendering.HelloWorkflow.State
import com.squareup.sample.hellocomposerendering.HelloWorkflow.State.Goodbye
import com.squareup.sample.hellocomposerendering.HelloWorkflow.State.Hello
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow.ui.compose.ComposeRendering

@OptIn(WorkflowUiExperimentalApi::class)
object HelloWorkflow : StatefulWorkflow<Unit, State, Nothing, ComposeRendering>() {
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
  ): ComposeRendering =
    context.renderChild(HelloRenderingWorkflow, renderState.name) { helloAction }

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)
}
