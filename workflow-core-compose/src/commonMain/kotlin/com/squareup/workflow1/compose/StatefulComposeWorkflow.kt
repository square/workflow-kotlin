package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow

interface StatefulComposeWorkflow<in PropsT, StateT, out OutputT, out RenderingT> {

  @Composable
  public fun Rendering(
    renderProps: PropsT,
    renderState: StateT,
    context: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext,
  ): RenderingT
}

fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.asComposeWorkflow():
  StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT> {
  val originalWorkflow = this
  return object : StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
    StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>() {

    @Composable
    override fun Rendering(
      renderProps: PropsT,
      renderState: StateT,
      context: RenderContext
    ): RenderingT = render(renderProps, renderState, context)

    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ): StateT = originalWorkflow.initialState(props, snapshot)

    override fun snapshotState(state: StateT): Snapshot? = originalWorkflow.snapshotState(state)

    override fun render(
      renderProps: PropsT,
      renderState: StateT,
      context: RenderContext
    ): RenderingT = originalWorkflow.render(renderProps, renderState, context)
  }
}

