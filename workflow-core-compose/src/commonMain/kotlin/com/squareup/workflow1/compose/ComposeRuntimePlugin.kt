package com.squareup.workflow1.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.remember
import app.cash.molecule.RecompositionClock.ContextClock
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus

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

  private val nextComposeFrameGate = Channel<Unit>(CONFLATED).apply {
    // Bootstrap with the gate open since first compose is synchronous.
    trySend(Unit)
  }
  private val composeRuntimeClock: BroadcastFrameClock = BroadcastFrameClock {
    // When we have the Recomposer waiting, then open the gate.
    nextComposeFrameGate.trySend(Unit)
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

    return clockedScope.launchMolecule(clock = ContextClock) {
      val runner = remember(workflowRunner) { (workflowRunner as WorkflowComposeRunner) }
      runner.nextComposedRendering()
    }
  }

  override suspend fun nextRendering() {
    nextComposeFrameGate.receive()
    // Gate is open, so send the frame to the Recomposer and wait to receive the signal that the
    // rendering has been composed.
    composeRuntimeClock.sendFrame(0L)
  }

  override fun chainedInterceptors(interceptors: List<WorkflowInterceptor>): WorkflowInterceptor =
    interceptors.map { it.asComposeWorkflowInterceptor() }.chained()
}
