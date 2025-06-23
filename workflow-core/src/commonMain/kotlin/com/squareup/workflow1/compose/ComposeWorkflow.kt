package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.IdCacheable
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi

/**
 * TODO
 */
@WorkflowExperimentalApi
public abstract class ComposeWorkflow<PropsT, OutputT, out RenderingT> :
  Workflow<PropsT, OutputT, RenderingT>,
  IdCacheable {

  /**
   * TODO
   */
  @WorkflowComposable
  @Composable
  protected abstract fun produceRendering(
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
