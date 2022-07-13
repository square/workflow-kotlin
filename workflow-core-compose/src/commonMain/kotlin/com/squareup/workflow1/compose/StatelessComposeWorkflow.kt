package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime

/**
 * @see [StatelessWorkflow]. This is the extension of that which supports the Compose runtime
 * optimizations for the children of this Workflow - i.e. Rendering() will not be called if the
 * state of children has not changed.
 *
 * * N.B. This is easily confused with
 * [com.squareup.sample.compose.hellocomposeworkflow.ComposeWorkflow] which is a sample showing a
 * much more radical modification of the Workflow API to support using Compose directly for more
 * than just render() optimizations.
 */
@WorkflowExperimentalRuntime
public abstract class StatelessComposeWorkflow<in PropsT, out OutputT, out RenderingT> :
  StatelessWorkflow<PropsT, OutputT, RenderingT>() {

  @Suppress("UNCHECKED_CAST", "DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
  public inner class RenderContext constructor(
    baseContext: BaseComposeRenderContext<PropsT, *, OutputT>
  ) : StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext(baseContext),
    BaseComposeRenderContext<@UnsafeVariance PropsT, Nothing, @UnsafeVariance OutputT> by
    baseContext as BaseComposeRenderContext<PropsT, Nothing, OutputT>

  override val statefulWorkflow: StatefulWorkflow<PropsT, Unit, OutputT, RenderingT>
    get() {
      @Suppress("LocalVariableName")
      val Rendering: @Composable BaseComposeRenderContext<PropsT, Unit, OutputT>
      .(props: PropsT, _: Unit) -> RenderingT =
        @Composable { props, _ ->
          val context = remember(this, this@StatelessComposeWorkflow) {
            ComposeRenderContext(this, this@StatelessComposeWorkflow)
          }
          (this@StatelessComposeWorkflow).Rendering(props, context)
        }
      return Workflow.composedStateful(
        initialState = { Unit },
        render = { props, _ ->
          render(
            props,
            RenderContext(
              this,
              this@StatelessComposeWorkflow as StatelessWorkflow<PropsT, OutputT, RenderingT>
            )
          )
        },
        Rendering = Rendering
      )
    }

  @Composable
  public abstract fun Rendering(
    renderProps: PropsT,
    context: RenderContext,
  ): RenderingT
}

/**
 * Turn this [StatelessWorkflow] into a [StatelessComposeWorkflow] with the [Rendering] function.
 *
 * If none is provided, it will default to calling [StatelessWorkflow.render].
 */
@WorkflowExperimentalRuntime
public fun <PropsT, OutputT, RenderingT>
StatelessWorkflow<PropsT, OutputT, RenderingT>.asComposeWorkflow(
  RenderingImpl: @Composable StatelessComposeWorkflow<PropsT, OutputT, RenderingT>.(
    PropsT,
    StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext
  ) -> RenderingT = { p, rc ->
    render(p, rc)
  }
): StatelessComposeWorkflow<PropsT, OutputT, RenderingT> {
  val originalWorkflow = this
  if (originalWorkflow is StatelessComposeWorkflow) {
    return originalWorkflow
  }
  return object : StatelessComposeWorkflow<PropsT, OutputT, RenderingT>() {

    @Composable
    override fun Rendering(
      renderProps: PropsT,
      context: RenderContext
    ): RenderingT = RenderingImpl(renderProps, context)

    override fun render(
      renderProps: PropsT,
      context: StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext
    ): RenderingT = originalWorkflow.render(renderProps, context)
  }
}

/**
 * Creates a [StatelessComposeWorkflow.RenderContext] from a [BaseComposeRenderContext]
 * for the given [StatelessComposeWorkflow].
 */
@WorkflowExperimentalRuntime
@Suppress("UNCHECKED_CAST")
public fun <PropsT, OutputT, RenderingT> ComposeRenderContext(
  baseContext: BaseComposeRenderContext<PropsT, *, OutputT>,
  workflow: StatelessComposeWorkflow<PropsT, OutputT, RenderingT>
): StatelessComposeWorkflow<PropsT, OutputT, RenderingT>.RenderContext =
  (baseContext as? StatelessComposeWorkflow<PropsT, OutputT, RenderingT>.RenderContext)
    ?: workflow.RenderContext(baseContext)

/**
 * Returns a stateless, composed [Workflow] via the given [render] function.
 *
 * Note that while the returned workflow doesn't have any _internal_ state of its own, it may use
 * [props][PropsT] received from its parent, and it may render child workflows that do have
 * their own internal state.
 */
@WorkflowExperimentalRuntime
public inline fun <PropsT, OutputT, RenderingT> Workflow.Companion.composedStateless(
  noinline Rendering: @Composable BaseComposeRenderContext<PropsT, Nothing, OutputT>.(
    props: PropsT
  ) -> RenderingT,
  crossinline render: BaseRenderContext<PropsT, Nothing, OutputT>.(props: PropsT) -> RenderingT,
): Workflow<PropsT, OutputT, RenderingT> =
  object : StatelessComposeWorkflow<PropsT, OutputT, RenderingT>() {
    override fun render(
      renderProps: PropsT,
      context: StatelessWorkflow<PropsT, OutputT, RenderingT>.RenderContext
    ): RenderingT = render(context, renderProps)

    @Composable
    override fun Rendering(
      renderProps: PropsT,
      context: RenderContext
    ): RenderingT = Rendering(context, renderProps)
  }
