package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi

// TODO mark these with a separate InternalWorkflow annotation

@WorkflowExperimentalApi
@Stable
public interface ComposeWorkflowInterceptor {

  @Composable
  fun <PropsT, OutputT, RenderingT> renderChild(
    childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    proceed: @Composable (
      childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
      props: PropsT,
      onOutput: ((OutputT) -> Unit)?,
    ) -> RenderingT
  ): RenderingT
}
