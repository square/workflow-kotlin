package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkStealingDispatcher
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.getValue
import com.squareup.workflow1.internal.requireSend
import com.squareup.workflow1.internal.setValue
import com.squareup.workflow1.internal.threadLocalOf
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.SelectBuilder

internal class ComposeWorkflowNode<P, O, R>(
  id: WorkflowNodeId,
  workflow: Workflow<P, O, R>,
  initialProps: P,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<O>) -> ActionProcessingResult = { it },
  parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null,
) : WorkflowNode<P, O, R>(
  id = id,
  baseContext = baseContext,
  interceptor = interceptor,
  emitAppliedActionToParent = emitAppliedActionToParent,
) {
  private val dispatcher = WorkStealingDispatcher(
    scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Unconfined,
  )
  private var workflow: Workflow<P, O, R> by mutableStateOf(workflow)
  private var rendering: R? = null
  private val recomposeRequests = Channel<Unit>(capacity = 1)
  private val outputs = Channel<O>(capacity = 1000)
  private val clock = BroadcastFrameClock(onNewAwaiters = ::onRecompositionRequested)
  private var props by mutableStateOf(initialProps)
  private val saveableStateRegistry = createSaveableStateRegistryForTreeSnapshot(snapshot)
  private var inRenderPass by threadLocalOf { false }

  override lateinit var session: WorkflowSession

  init {
    val composableRuntimeConfig = WorkflowComposableRuntimeConfig(
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      workflowInterceptor = interceptor,
      idCounter = idCounter,
    )

    scope.launchMolecule(
      mode = RecompositionMode.ContextClock,
      context = clock + dispatcher,
      emitter = { rendering = it },
    ) {
      withCompositionLocals(LocalSaveableStateRegistry provides saveableStateRegistry) {
        renderWorkflow(
          workflow = this.workflow,
          props = props,
          onOutput = outputs::requireSend,
          renderKey = "",
          parentSession = parent,
          config = composableRuntimeConfig,
          onSessionAvailable = { session = it },
        )
      }
    }
  }

  override fun render(workflow: Workflow<P, O, R>, input: P): R {
    // Prevent any recompose requests from hitting the channel since we're about to handle them.
    inRenderPass = true
    try {
      this.workflow = workflow
      this.props = input

      // Since we just changed some states, apply them to the global snapshot to ensure the
      // composition sees them.
      Snapshot.sendApplyNotifications()

      // Pump the dispatcher to force the recomposition loop to continue and request a frame.
      dispatcher.advanceUntilIdle()

      // Consume the requested frame so it doesn't get fired again from the side effect.
      recomposeRequests.tryReceive()

      // Send the frame to perform recomposition and effects.
      // Hard-code unchanging frame time since there's no actual frame time code shouldn't rely on
      // this value.
      clock.sendFrame(0L)
      return rendering!!
    } finally {
      inRenderPass = false
    }
  }

  override fun snapshot(): TreeSnapshot {
    val savedValues = saveableStateRegistry.performSave()
    val snapshot = savedValuesToSnapshot(savedValues)
    return TreeSnapshot(snapshot, ::emptyMap)
  }

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    with(selector) {
      outputs.onReceive { output ->
        val actionApplied = ActionApplied(output = WorkflowOutput(output), stateChanged = false)
        emitAppliedActionToParent(actionApplied)
      }

      recomposeRequests.onReceive {
        val actionApplied = ActionApplied<O>(output = null, stateChanged = true)
        emitAppliedActionToParent(actionApplied)
      }
    }
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult {
    // TODO
    println("OMG TODO implement applyNextAvailableTreeAction")
    return ActionsExhausted
  }

  private fun onRecompositionRequested() {
    // If the dispatcher pump in render() resumes the recomposer's loop, then it will request a
    // frame in the render pass but we don't need to send that to the channel since it's about to
    // be processed by render() directly.
    if (!inRenderPass) {
      recomposeRequests.trySend(Unit)
    }
  }
}
