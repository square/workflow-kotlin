package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Immutable
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.IdCounter

/**
 * Defines configuration used by [renderWorkflow] when rendering workflows.
 *
 * This class just groups a bunch of parameters together that have the following properties:
 * - They are needed by every workflow "node".
 * - They are the same throughout the entire workflow runtime tree.
 * - They never change over the course of the workflow runtime.
 *
 * It's just a convenience class.
 */
@Immutable
internal class WorkflowComposableRuntimeConfig(
  val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  val workflowTracer: WorkflowTracer? = null,
  val workflowInterceptor: WorkflowInterceptor? = null,
  val idCounter: IdCounter? = null,
) {

  override fun toString(): String =
    "WorkflowComposableRuntimeConfig(" +
      "runtimeConfig=$runtimeConfig, " +
      "workflowTracer=$workflowTracer)"
}
