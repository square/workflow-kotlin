package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime

/**
 * @see [StatefulWorkflow]. This is the extension of that which supports the Compose runtime
 * optimizations for the children of this Workflow - i.e. Rendering() will not be called if the
 * state of children has not changed.
 *
 * N.B. This is easily confused with
 * [com.squareup.sample.compose.hellocomposeworkflow.ComposeWorkflow] which is a sample showing a
 * much more radical modification of the Workflow API to support using Compose directly for more
 * than just render() optimizations.
 */
@WorkflowExperimentalRuntime
public abstract class StatefulComposeWorkflow<in PropsT, StateT, out OutputT, out RenderingT> :
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>() {

  @Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
  public inner class RenderContext internal constructor(
    baseContext: BaseComposeRenderContext<PropsT, StateT, OutputT>
  ) : StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext(baseContext),
    BaseComposeRenderContext<@UnsafeVariance PropsT, StateT, @UnsafeVariance OutputT> by baseContext

  @Composable
  public abstract fun Rendering(
    renderProps: PropsT,
    renderState: StateT,
    context: RenderContext,
  ): RenderingT
}

/**
 * Turn this [StatefulWorkflow] into a [StatefulComposeWorkflow] with the [RenderingImpl] function.
 *
 * If none is provided, it will default to calling [StatefulWorkflow.render].
 */
@WorkflowExperimentalRuntime
public fun <PropsT, StateT, OutputT, RenderingT>
StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.asComposeWorkflow(
  RenderingImpl: @Composable StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>.(
    PropsT,
    StateT,
    StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext
  ) -> RenderingT = { p, s, rc ->
    render(p, s, rc)
  }
):
  StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT> {
  val originalWorkflow = this
  if (originalWorkflow is StatefulComposeWorkflow) {
    return originalWorkflow
  }
  return object : StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>() {

    @Composable
    override fun Rendering(
      renderProps: PropsT,
      renderState: StateT,
      context: RenderContext
    ): RenderingT = RenderingImpl(renderProps, renderState, context)

    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ): StateT = originalWorkflow.initialState(props, snapshot)

    override fun snapshotState(state: StateT): Snapshot? = originalWorkflow.snapshotState(state)

    override fun render(
      renderProps: PropsT,
      renderState: StateT,
      context: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext
    ): RenderingT = originalWorkflow.render(renderProps, renderState, context)
  }
}

/**
 * Creates a [StatefulComposeWorkflow.RenderContext] from a [BaseComposeRenderContext] for the given
 * [StatefulComposeWorkflow].
 */
@WorkflowExperimentalRuntime
@Suppress("UNCHECKED_CAST")
public fun <PropsT, StateT, OutputT, RenderingT> ComposeRenderContext(
  baseContext: BaseComposeRenderContext<PropsT, StateT, OutputT>,
  workflow: StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>
): StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext =
  (baseContext as? StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext)
    ?: workflow.RenderContext(baseContext)

/**
 * Returns a Composed stateful [Workflow], defined by the given functions.
 */
@WorkflowExperimentalRuntime
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.composedStateful(
  crossinline initialState: (PropsT, Snapshot?) -> StateT,
  crossinline render: BaseRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT,
  noinline Rendering: @Composable BaseComposeRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT = { props, state ->
    render(props, state)
  },
  crossinline snapshot: (StateT) -> Snapshot?,
  crossinline onPropsChanged: (
    old: PropsT,
    new: PropsT,
    state: StateT
  ) -> StateT = { _, _, state -> state }
): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT> =
  object : StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>() {
    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ): StateT = initialState(props, snapshot)

    override fun onPropsChanged(
      old: PropsT,
      new: PropsT,
      state: StateT
    ): StateT = onPropsChanged(old, new, state)

    override fun render(
      renderProps: PropsT,
      renderState: StateT,
      context: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.RenderContext
    ): RenderingT = render(context, renderProps, renderState)

    override fun snapshotState(state: StateT) = snapshot(state)

    @Composable
    override fun Rendering(
      renderProps: PropsT,
      renderState: StateT,
      context: RenderContext,
    ): RenderingT = Rendering(context, renderProps, renderState)
  }

/**
 * Returns a Composed stateful [Workflow], with no props, implemented via the given functions.
 */
@WorkflowExperimentalRuntime
public fun <StateT, OutputT, RenderingT> Workflow.Companion.composedStateful(
  initialState: (Snapshot?) -> StateT,
  render: BaseRenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT,
  Rendering: @Composable BaseComposeRenderContext<Unit, StateT, OutputT>.(
    state: StateT
  ) -> RenderingT,
  snapshot: (StateT) -> Snapshot?
): StatefulWorkflow<Unit, StateT, OutputT, RenderingT> {
  @Suppress("LocalVariableName")
  val RenderingWithProps: @Composable BaseComposeRenderContext<Unit, StateT, OutputT>.(
    props: Unit,
    state: StateT
  ) -> RenderingT = @Composable { _: Unit, state: StateT ->
    Rendering(state)
  }
  return composedStateful(
    initialState = { _: Unit, initialSnapshot: Snapshot? -> initialState(initialSnapshot) },
    render = { _: Unit, state: StateT -> render(state) },
    Rendering = RenderingWithProps,
    snapshot = snapshot
  )
}

/**
 * Returns a Composed stateful [Workflow] implemented via the given functions.
 *
 * This overload does not support snapshotting, but there are other overloads that do.
 */
@WorkflowExperimentalRuntime
public inline fun <PropsT, StateT, OutputT, RenderingT> Workflow.Companion.composedStateful(
  crossinline initialState: (PropsT) -> StateT,
  crossinline render: BaseRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT,
  noinline Rendering: @Composable BaseComposeRenderContext<PropsT, StateT, OutputT>.(
    props: PropsT,
    state: StateT
  ) -> RenderingT,
  crossinline onPropsChanged: (
    old: PropsT,
    new: PropsT,
    state: StateT
  ) -> StateT = { _, _, state -> state }
): StatefulWorkflow<PropsT, StateT, OutputT, RenderingT> = composedStateful(
  initialState = { props: PropsT, _ -> initialState(props) },
  render = render,
  Rendering = Rendering,
  snapshot = { null },
  onPropsChanged = onPropsChanged
)

/**
 * Returns a Composed stateful [Workflow], with no props, implemented via the given function.
 *
 * This overload does not support snapshots, but there are others that do.
 */
@WorkflowExperimentalRuntime
public fun <StateT, OutputT, RenderingT> Workflow.Companion.composedStateful(
  initialState: StateT,
  render: BaseRenderContext<Unit, StateT, OutputT>.(state: StateT) -> RenderingT,
  Rendering: @Composable BaseComposeRenderContext<Unit, StateT, OutputT>.(
    state: StateT
  ) -> RenderingT,
): StatefulWorkflow<Unit, StateT, OutputT, RenderingT> {
  @Suppress("LocalVariableName")
  val RenderWithProps: @Composable BaseComposeRenderContext<Unit, StateT, OutputT>.(
    props: Unit,
    state: StateT
  ) -> RenderingT = @Composable { _: Unit, state: StateT ->
    Rendering(state)
  }
  return composedStateful(
    initialState = { initialState },
    render = { _, state -> render(state) },
    Rendering = RenderWithProps,
  )
}
