package com.squareup.workflow1.compose

import androidx.compose.runtime.BroadcastFrameClock
import app.cash.molecule.launchMolecule
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowRunner
import com.squareup.workflow1.WorkflowRuntimePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.yield

/**
 * [WorkflowRuntimePlugin] implementation that adds in a Compose optimized runtime. This will
 * attempt to prevent any unnecessary renderings when the state (tracked using Compose) has
 * not changed.
 *
 * Use [StatefulComposeWorkflow] and [StatelessComposeWorkflow] to take advantage of these
 * runtime optimizations if your [Workflow] is not a leaf in the tree. Leaf workflows will be
 * converted and handled automatically.
 */
@WorkflowExperimentalRuntime
public object ComposeRuntimePlugin : WorkflowRuntimePlugin {

  private var composeWaitingForFrame = false
  private val composeRuntimeClock = BroadcastFrameClock {
    composeWaitingForFrame = true
  }

  override fun <PropsT, OutputT, RenderingT> createWorkflowRunner(
    scope: CoroutineScope,
    protoWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: StateFlow<PropsT>,
    snapshot: TreeSnapshot?,
    interceptor: WorkflowInterceptor,
    runtimeConfig: RuntimeConfig
  ): WorkflowRunner<PropsT, OutputT, RenderingT> = WorkflowComposeRunner(
    scope,
    protoWorkflow,
    props,
    snapshot,
    interceptor.asComposeWorkflowInterceptor(),
    runtimeConfig,
  )

  override fun <PropsT, OutputT, RenderingT> initializeRenderingStream(
    workflowRunner: WorkflowRunner<PropsT, OutputT, RenderingT>,
    runtimeScope: CoroutineScope
  ): StateFlow<RenderingAndSnapshot<RenderingT>> {
    val clockedScope = runtimeScope + composeRuntimeClock

    return clockedScope.launchMolecule {
      (workflowRunner as WorkflowComposeRunner).nextComposedRendering()
    }
  }

  override suspend fun nextRendering() {
    if (composeWaitingForFrame) {
      composeWaitingForFrame = false
      composeRuntimeClock.sendFrame(0L)
      yield()
    }
  }

  override fun chainedInterceptors(interceptors: List<WorkflowInterceptor>): WorkflowInterceptor =
    interceptors.map { it.asComposeWorkflowInterceptor() }.chained()
}
