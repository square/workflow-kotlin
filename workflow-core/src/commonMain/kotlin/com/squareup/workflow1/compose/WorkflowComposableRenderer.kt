package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi

// TODO mark these with a separate InternalWorkflow annotation

@WorkflowExperimentalApi
public val LocalWorkflowComposableRenderer =
  staticCompositionLocalOf<WorkflowComposableRenderer> { error("No renderer") }

@WorkflowExperimentalApi
public interface WorkflowComposableRenderer {

  @Composable
  fun <PropsT, OutputT, RenderingT> renderChild(
    childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?
  ): RenderingT
}
