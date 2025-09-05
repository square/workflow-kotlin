package com.squareup.workflow1.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer

@WorkflowExperimentalApi
public val LocalWorkflowComposableRuntimeConfig = staticCompositionLocalOf {
  WorkflowComposableRuntimeConfig()
}

@WorkflowExperimentalApi
@Immutable
public class WorkflowComposableRuntimeConfig(
  val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  val workflowTracer: WorkflowTracer? = null,
  val interceptor: ComposeWorkflowInterceptor? = null,
) {

  override fun toString(): String = "WorkflowComposableRuntimeConfig(" +
    "runtimeConfig=$runtimeConfig, " +
    "workflowTracer=$workflowTracer," +
    "interceptor=$interceptor" +
    ")"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as WorkflowComposableRuntimeConfig

    if (runtimeConfig != other.runtimeConfig) return false
    if (workflowTracer != other.workflowTracer) return false
    if (interceptor != other.interceptor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = runtimeConfig.hashCode()
    result = 31 * result + (workflowTracer?.hashCode() ?: 0)
    result = 31 * result + (interceptor?.hashCode() ?: 0)
    return result
  }
}
