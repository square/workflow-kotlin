package com.squareup.sample.compose.hellocomposeworkflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.contraMap
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.sample.compose.hellocomposeworkflow.ComposeWorkflowImpl.State
import com.squareup.workflow1.ui.compose.ComposeRendering

@WorkflowUiExperimentalApi
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

    return State(propsHolder, sinkHolder, object : ComposeRendering {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
        // The sink will get set on the first render pass, which must happen before this is first
        // composed, so it should never be null.
        val sink = sinkHolder.sink!!
        // Important: Use the props from the MutableState, _not_ the one passed into render.
        workflow.RenderingContent(propsHolder.value, sink, viewEnvironment)
      }
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
    renderProps: PropsT,
    renderState: State<PropsT, OutputT>,
    context: RenderContext
  ): ComposeRendering {
    // The first render pass needs to cache the sink. The sink is reusable, so we can just pass the
    // same one every time.
    if (renderState.sinkHolder.sink == null) {
      renderState.sinkHolder.sink = context.actionSink.contraMap(::forwardOutput)
    }

    // onPropsChanged will ensure the rendering is re-composed when the props changes.
    return renderState.rendering
  }

  // Compiler bug doesn't let us call Snapshot.EMPTY.
  override fun snapshotState(state: State<PropsT, OutputT>): Snapshot = Snapshot.of("")

  private fun forwardOutput(output: OutputT) = action { setOutput(output) }
}
