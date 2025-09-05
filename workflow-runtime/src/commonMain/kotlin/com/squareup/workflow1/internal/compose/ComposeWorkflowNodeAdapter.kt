package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.LocalWorkflowComposableRuntimeConfig
import com.squareup.workflow1.compose.WorkflowComposableRuntimeConfig
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.compose.runtime.launchSynchronizedMolecule
import com.squareup.workflow1.internal.requireSend
import com.squareup.workflow1.trace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

private const val OUTPUT_QUEUE_LIMIT = 1_000

internal fun log(message: String) = message.lines().forEach {
  // println("WorkflowComposableNode $it")
}

/**
 * Entry point into the Compose runtime from the Workflow runtime.
 *
 * This node hosts a compose runtime and synchronizes its recompositions to workflow render passes.
 * Most of the workflow work (interception, action propagation) is delegated to a
 * [ComposeWorkflowChildNode]. [ComposeWorkflow]s nested directly under this one do not have their
 * own composition, they share this one.
 */
@OptIn(WorkflowExperimentalApi::class)
internal class ComposeWorkflowNodeAdapter<PropsT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  private val workflowTracer: WorkflowTracer? = null,
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  parentSession: WorkflowSession?,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter = IdCounter()
) :
  WorkflowNode<PropsT, OutputT, RenderingT>(
    id = id,
    baseContext = baseContext,
    interceptor = interceptor,
    emitAppliedActionToParent = emitAppliedActionToParent,
  ) {

  private val recomposeChannel = Channel<Unit>(capacity = 1)
  private val molecule = workflowTracer.trace("ComposeWorkflowAdapterInstantiateCompose") {
    scope.launchSynchronizedMolecule(
      onNeedsRecomposition = { recomposeChannel.trySend(Unit) }
    )
  }

  /** This does not need to be a snapshot state object, it's only set again by [snapshot]. */
  private var snapshotCache = snapshot?.childTreeSnapshots
  private val saveableStateRegistry =
    restoreSaveableStateRegistryFromSnapshot(snapshot?.workflowSnapshot)

  private val outputsChannel = Channel<OutputT>(capacity = OUTPUT_QUEUE_LIMIT)

  /**
   * Function invoked when [onNextAction] receives an output from [outputsChannel].
   */
  private val processOutputFromChannel: (OutputT) -> ActionProcessingResult = { output ->
    log("got output from channel: $output")

    // Ensure any state updates performed by the output sender gets to invalidate any
    // compositions that read them, so we can check needsRecompose below.
    Snapshot.sendApplyNotifications()

    // ComposeWorkflowChildNode can't tell if its own state changed since that information about
    // specific composables/recompose scopes is only visible inside the compose runtime, so
    // individual ComposeWorkflow nodes always report no state changes (unless they have a
    // traditional child that reported a state change).
    // However, we *can* check if any state changed that was read by anything in the
    // composition, so when an action bubbles up to here, the top of the composition, we use
    // that information to set the state changed flag if necessary.
    val actionApplied = ActionApplied(
      output = WorkflowOutput(output),
      stateChanged = molecule.needsRecomposition
    )

    // Don't bubble up if no state changed and there was no output.
    log("adapter node propagating action cascade up (aggregateAction=$actionApplied)")
    emitAppliedActionToParent(actionApplied)
  }

  /**
   * Function invoked when [onNextAction] receives a recompose request.
   * This handles the case where some state read by the composition is changed but emitOutput is
   * not called.
   */
  private val processRecompositionRequestFromChannel: (Unit) -> ActionProcessingResult = {
    // A pure frame request means compose state was updated that the composition read, but
    // emitOutput was not called, so we don't have any outputs to report.
    val applied = ActionApplied<OutputT>(
      output = null,
      // needsRecomposition should always be true now since the runtime explicitly requested
      // recomposition, but check anyway.
      stateChanged = molecule.needsRecomposition
    )

    // Propagate the action up the workflow tree.
    log("frame request received from channel, sending no output to parent: $applied")
    emitAppliedActionToParent(applied)
  }

  override val session: WorkflowSession = parentSession.createChild(
    id = id,
    idCounter = idCounter,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
  )

  override fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT {
    // Ensure that recomposer has a chance to process any state changes from the action cascade that
    // triggered this render before we check for a frame.
    log("render sending apply notifications again needsRecompose=${molecule.needsRecomposition}")
    // TODO Consider pulling this up into the workflow runtime loop, since we only need to run it
    //  once before the entire tree renders, not at every level. In fact, if this is only here to
    //  ensure cachedComposeWorkflow and lastProps are seen, that will only work if this
    //  ComposeWorkflow is not nested below another traditional and compose workflow, since anything
    //  rendering under the first CW will be in a snapshot.
    Snapshot.sendApplyNotifications()
    log("sent apply notifications, needsRecompose=${molecule.needsRecomposition}")

    // If this re-render was not triggered by the channel handler, then clear it so we don't
    // immediately trigger another redundant render pass after this.
    recomposeChannel.tryReceive()

    // It is very likely that this will be a noop: any time the workflow runtime is doing a
    // render pass and no state read by our composition changed, there shouldn't be a frame request.
    workflowTracer.trace("ComposeWorkflowAdapterRecomposeWithContent") {
      return molecule.recomposeWithContent {
        workflowTracer?.beginSection("ComposeWorkflowAdapterProduceRendering")
        produceRendering(
          workflow = workflow,
          props = input
        ).also {
          workflowTracer?.endSection()
        }
      }
    }
  }

  private val workflowComposeRuntimeConfig = WorkflowComposableRuntimeConfig(
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    interceptor = interceptor.asComposeWorkflowInterceptor(
      idCounter = idCounter,
      parentSession = parentSession,
    ),
  )

  @OptIn(WorkflowExperimentalApi::class)
  @Composable
  private fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT = withCompositionLocals(
    LocalSaveableStateRegistry provides saveableStateRegistry,
    LocalWorkflowComposableRuntimeConfig provides workflowComposeRuntimeConfig,
  ) {
    com.squareup.workflow1.compose.renderChild(
      workflow = workflow,
      props = props,
      onOutput = outputsChannel::requireSend
    )
  }

  override fun snapshot(): TreeSnapshot {
    // Get rid of any snapshots that weren't applied on the first render pass.
    // They belong to children that were saved but not restarted.
    snapshotCache = null

    val workflowSnapshot = saveSaveableStateRegistryToSnapshot(saveableStateRegistry)
    return TreeSnapshot.forRootOnly(workflowSnapshot)
  }

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    with(selector) {
      // We must register for child actions before frame requests, because selection is
      // strongly-ordered: If multiple subjects become available simultaneously, then the one whose
      // receiver was registered first will fire first. We always want to handle outputs first because
      // the output handler will implicitly also handle frame requests. If a frame request happens at
      // the same time or the output handler enqueues a frame request, then the subsequent render pass
      // will dequeue the frame request itself before the next call to register.
      outputsChannel.onReceive(processOutputFromChannel)

      // If there's a frame request, then some state changed, which is equivalent to the traditional
      // case of a WorkflowAction being enqueued that just modifies state.
      recomposeChannel.onReceive(processRecompositionRequestFromChannel)
    }
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult {
    if (skipDirtyNodes && molecule.needsRecomposition) return ActionsExhausted

    // If none of our children had any actions to process, then we can process any outputs of our
    // own.
    val pendingOutput = outputsChannel.tryReceive().getOrNull()
    if (pendingOutput != null) {
      return processOutputFromChannel(pendingOutput)
    }

    // If no child nodes had any actions to process, then we can check if we need to recompose,
    // which means some composition state changed and is equivalent to the traditional case of a
    // WorkflowAction being enqueued that just modifies state.
    if (molecule.needsRecomposition) {
      // Consume the request since we're going to process it directly. The channel just contains
      // Unit though so we don't actually care what the result of the receive is.
      recomposeChannel.tryReceive()
      return processRecompositionRequestFromChannel(Unit)
    }

    return ActionsExhausted
  }
}
