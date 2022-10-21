package com.squareup.workflow1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * A plugin mechanism to provide a way to define runtime optimization behaviour that requires
 * Compiler optimizations and extensive dependencies (such as Compose) in a separate module.
 */
public interface WorkflowRuntimePlugin {

  /**
   * Initialize the stream of [RenderingAndSnapshot] that the UI layer will receive.
   */
  public fun <PropsT, OutputT, RenderingT> initializeRenderingStream(
    workflowRunner: WorkflowRunner<PropsT, OutputT, RenderingT>,
    runtimeScope: CoroutineScope
  ): StateFlow<RenderingAndSnapshot<RenderingT>>

  /**
   * Create a [WorkflowRunner] to drive the root [WorkflowNode].
   */
  public fun <PropsT, OutputT, RenderingT> createWorkflowRunner(
    scope: CoroutineScope,
    protoWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: StateFlow<PropsT>,
    snapshot: TreeSnapshot?,
    interceptor: WorkflowInterceptor,
    runtimeConfig: RuntimeConfig
  ): WorkflowRunner<PropsT, OutputT, RenderingT>

  /**
   * Trigger the next rendering in the runtime.
   */
  public suspend fun nextRendering()

  /**
   * Create a chain of interceptors for all that are passed in to [renderWorkflowIn]
   */
  public fun chainedInterceptors(interceptors: List<WorkflowInterceptor>): WorkflowInterceptor
}
