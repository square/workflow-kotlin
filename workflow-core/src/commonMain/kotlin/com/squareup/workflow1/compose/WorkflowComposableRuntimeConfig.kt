@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.internal.withCompositionLocals

/**
 * Provides a value of [WorkflowComposableRuntimeConfig] to the root of a workflow tree.
 *
 * Values provided inside the subtree (i.e. inside [ComposeWorkflow]s) are ignored.
 */
@WorkflowExperimentalApi
public val LocalWorkflowComposableRuntimeConfig = staticCompositionLocalOf {
  WorkflowComposableRuntimeConfig()
}

/**
 * Defines configuration used by [renderChild] when rendering workflows.
 *
 * This is an immutable class with value semantics.
 *
 * Provide an instance of this via [LocalWorkflowComposableRuntimeConfig]. Providing a _different_
 * value between recompositions will cause the entire workflow subtree to be torn down and
 * recreated, so probably don't do that. Values provided inside [ComposeWorkflow] calls will be
 * ignored.
 */
// Impl note: This is not a data class since it's public API, but its implementation of equals is
// critical since providing a different instance resets the entire workflow tree.
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

/**
 * Propagates the value of the top-level [LocalWorkflowComposableRuntimeConfig] to the rest of the
 * subtree. This is to ignore any lower-level provisions.
 */
private val LocalInternalWorkflowComposableRuntimeConfig =
  staticCompositionLocalOf<WorkflowComposableRuntimeConfig?> { null }

/**
 * Gets the [LocalWorkflowComposableRuntimeConfig] from the root of the workflow tree, preventing
 * overriding at lower levels.
 */
@Composable
internal fun <R> withFixedWorkflowComposableRuntimeConfig(
  content: @Composable (WorkflowComposableRuntimeConfig) -> R
): R {
  val internalConfig = LocalInternalWorkflowComposableRuntimeConfig.current
  // This branching is OK since the same branch is taken for every call – the branching is
  // determined by the call tree, and so changing that would reset state anyway.
  if (internalConfig != null) {
    return content(internalConfig)
  } else {
    val rootConfig = LocalWorkflowComposableRuntimeConfig.current
    return withCompositionLocals(LocalInternalWorkflowComposableRuntimeConfig provides rootConfig) {
      content(rootConfig)
    }
  }
}
