package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.IdCacheable
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier

/**
 * TODO
 */
@WorkflowExperimentalApi
public abstract class ComposeWorkflow<PropsT, OutputT, out RenderingT> :
  Workflow<PropsT, OutputT, RenderingT>,
  IdCacheable {

  /**
   * Use a lazy delegate so that any [ImpostorWorkflow.realIdentifier] will have been computed
   * before this is initialized and cached.
   */
  override var cachedIdentifier: WorkflowIdentifier? = null

  /**
   * TODO
   */
  @WorkflowComposable
  @Composable
  abstract fun produceRendering(
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ): RenderingT

  final override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, OutputT, RenderingT> {
    throw UnsupportedOperationException(
      "This version of the Compose runtime does not support ComposeWorkflow. " +
        "Please upgrade your workflow-runtime."
    )
  }
}

@WorkflowExperimentalApi
@WorkflowComposable
@Composable
public fun <PropsT, OutputT, RenderingT> renderChild(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?
): RenderingT {
  val renderer = LocalWorkflowComposableRenderer.current
  return renderer.renderChild(workflow, props, onOutput)
}

@WorkflowExperimentalApi
@WorkflowComposable
@Composable
inline fun <OutputT, RenderingT> renderChild(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  noinline onOutput: ((OutputT) -> Unit)?
): RenderingT = renderChild(workflow, props = Unit, onOutput)

@WorkflowExperimentalApi
@WorkflowComposable
@Composable
inline fun <PropsT, RenderingT> renderChild(
  workflow: Workflow<PropsT, Nothing, RenderingT>,
  props: PropsT,
): RenderingT = renderChild(workflow, props, onOutput = null)

@WorkflowExperimentalApi
@WorkflowComposable
@Composable
inline fun <RenderingT> renderChild(
  workflow: Workflow<Unit, Nothing, RenderingT>,
): RenderingT = renderChild(workflow, props = Unit, onOutput = null)
