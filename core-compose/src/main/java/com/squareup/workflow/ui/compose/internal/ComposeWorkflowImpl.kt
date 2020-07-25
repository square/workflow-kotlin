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
package com.squareup.workflow.ui.compose.internal

import androidx.compose.MutableState
import androidx.compose.mutableStateOf
import androidx.compose.structuralEqualityPolicy
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Sink
import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.action
import com.squareup.workflow.ui.compose.internal.ComposeWorkflowImpl.State
import com.squareup.workflow.contraMap
import com.squareup.workflow.ui.compose.ComposeRendering
import com.squareup.workflow.ui.compose.ComposeWorkflow

internal class ComposeWorkflowImpl<PropsT, OutputT : Any>(
  private val workflow: ComposeWorkflow<PropsT, OutputT>
) : StatefulWorkflow<PropsT, State<PropsT, OutputT>, OutputT, ComposeRendering>() {

  // This doesn't need to be a @Model, it only gets set once, before the composable ever runs.
  class SinkHolder<OutputT>(var sink: Sink<OutputT>? = null)

  data class State<PropsT, OutputT>(
    val propsHolder: MutableState<PropsT>,
    val sinkHolder: SinkHolder<OutputT>,
    val rendering: ComposeRendering
  )

  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): State<PropsT, OutputT> {
    val propsHolder = mutableStateOf(props, policy = structuralEqualityPolicy())
    val sinkHolder = SinkHolder<OutputT>()
    return State(propsHolder, sinkHolder, ComposeRendering { environment ->
      // The sink will get set on the first render pass, so it should never be null.
      val sink = sinkHolder.sink!!
      // Important: Use the props from the MutableState, _not_ the one passed into render.
      workflow.render(propsHolder.value, sink, environment)
    })
  }

  override fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: State<PropsT, OutputT>
  ): State<PropsT, OutputT> {
    state.propsHolder.value = new
    return state
  }

  override fun render(
    props: PropsT,
    state: State<PropsT, OutputT>,
    context: RenderContext<State<PropsT, OutputT>, OutputT>
  ): ComposeRendering {
    // The first render pass needs to cache the sink. The sink is reusable, so we can just pass the
    // same one every time.
    if (state.sinkHolder.sink == null) {
      state.sinkHolder.sink = context.actionSink.contraMap(::forwardOutput)
    }

    // onPropsChanged will ensure the rendering is re-composed when the props changes.
    return state.rendering
  }

  // Compiler bug doesn't let us call Snapshot.EMPTY.
  override fun snapshotState(state: State<PropsT, OutputT>): Snapshot = Snapshot.of("")

  private fun forwardOutput(output: OutputT) = action { setOutput(output) }
}
