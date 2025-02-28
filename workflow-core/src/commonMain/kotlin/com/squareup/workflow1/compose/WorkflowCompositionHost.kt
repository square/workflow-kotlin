package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi

// TODO @InternalWorkflowApi
@WorkflowExperimentalApi
public val LocalWorkflowCompositionHost: ProvidableCompositionLocal<WorkflowCompositionHost> =
  staticCompositionLocalOf { error("No WorkflowCompositionHost provided.") }

/**
 * Represents the owner of this [WorkflowComposable] composition.
 */
// TODO move these into a separate, internal-only, implementation-depended-on module to hide from
//  consumers by default?
// TODO @InternalWorkflowApi
@WorkflowExperimentalApi
@Stable
public interface WorkflowCompositionHost {

  /**
   * Renders a child [Workflow] and returns its rendering. See the top-level composable
   * [com.squareup.workflow1.compose.renderWorkflow] for main documentation.
   */
  @WorkflowComposable
  @Composable
  public fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    onOutput: ((ChildOutputT) -> Unit)?
  ): ChildRenderingT
}
