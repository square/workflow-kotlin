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
 * This is an immutable class with value semantics.
 */
// Impl note: This is not a data class since it's public API, but its implementation of equals is
// critical since providing a different instance resets the entire workflow tree.
@Immutable
internal class WorkflowComposableRuntimeConfig(
  val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  val workflowTracer: WorkflowTracer? = null,
  val workflowInterceptor: WorkflowInterceptor? = null,
  val idCounter: IdCounter? = null,
) {

  override fun toString(): String = "WorkflowComposableRuntimeConfig(" +
    "runtimeConfig=$runtimeConfig, " +
    "workflowTracer=$workflowTracer)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as WorkflowComposableRuntimeConfig

    if (runtimeConfig != other.runtimeConfig) return false
    if (workflowTracer != other.workflowTracer) return false

    return true
  }

  override fun hashCode(): Int {
    var result = runtimeConfig.hashCode()
    result = 31 * result + (workflowTracer?.hashCode() ?: 0)
    return result
  }
}
