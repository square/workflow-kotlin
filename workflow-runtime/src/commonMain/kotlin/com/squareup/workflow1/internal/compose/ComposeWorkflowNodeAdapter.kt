package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.internal.AbstractWorkflowNode
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.compose.runtime.SynchronizedMolecule
import com.squareup.workflow1.internal.compose.runtime.launchSynchronizedMolecule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

internal fun log(message: String) = message.lines().forEach {
  println("WorkflowComposableNode $it")
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
  workflow: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
  // TODO AbstractWorkflowNode should not implement WorkflowSession, since only StatefulWorkflowNode
  //  needs that. The composable session is implemented by ComposeWorkflowChildNode.
) : AbstractWorkflowNode<PropsT, OutputT, RenderingT>(
  id = id,
  runtimeConfig = runtimeConfig,
  workflowTracer = workflowTracer,
  parent = parent,
  baseContext = baseContext,
  idCounter = idCounter,
  interceptor = interceptor,
  emitAppliedActionToParent = emitAppliedActionToParent,
) {

  private val recomposeChannel = Channel<Unit>(capacity = 1)
  private val molecule: SynchronizedMolecule<RenderingT> = launchSynchronizedMolecule(
    onNeedsRecomposition = { recomposeChannel.trySend(Unit) }
  )

  private val childNode = ComposeWorkflowChildNode<PropsT, OutputT, RenderingT>(
    id = id,
    initialProps = initialProps,
    snapshot = snapshot,
    baseContext = coroutineContext,
    parent = parent,
    workflowTracer = workflowTracer,
    runtimeConfig = runtimeConfig,
    interceptor = interceptor,
    idCounter = idCounter,
    emitAppliedActionToParent = { actionApplied ->
      // Ensure any state updates performed by the output sender gets to invalidate any
      // compositions that read them, so we can check needsRecompose below.
      Snapshot.sendApplyNotifications()
      log(
        "adapter node sent apply notifications from action cascade (" +
          "actionApplied=$actionApplied, needsRecompose=${molecule.needsRecomposition})"
      )

      // ComposeWorkflowChildNode can't tell if its own state changed since that information about
      // specific composables/recompose scopes is only visible inside the compose runtime, so
      // individual ComposeWorkflow nodes always report no state changes (unless they have a
      // traditional child that reported a state change).
      // However, we *can* check if any state changed that was read by anything in the
      // composition, so when an action bubbles up to here, the top of the composition, we use
      // that information to set the state changed flag if necessary.
      val aggregateAction = if (molecule.needsRecomposition && !actionApplied.stateChanged) {
        actionApplied.copy(stateChanged = true)
      } else {
        actionApplied
      }

      // Don't bubble up if no state changed and there was no output.
      if (aggregateAction.stateChanged || aggregateAction.output != null) {
        log("adapter node propagating action cascade up (aggregateAction=$aggregateAction)")
        emitAppliedActionToParent(aggregateAction)
      } else {
        log("adapter node not propagating action cascade since nothing happened (aggregateAction=$aggregateAction)")
        aggregateAction
      }
    }
  )

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
    return molecule.recomposeWithContent {
      childNode.produceRendering(
        workflow = workflow,
        props = input
      )
    }
  }

  override fun snapshot(): TreeSnapshot = childNode.snapshot()

  override fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean {
    // We must register for child actions before frame requests, because selection is
    // strongly-ordered: If multiple subjects become available simultaneously, then the one whose
    // receiver was registered first will fire first. We always want to handle outputs first because
    // the output handler will implicitly also handle frame requests. If a frame request happens at
    // the same time or the output handler enqueues a frame request, then the subsequent render pass
    // will dequeue the frame request itself before the next call to onNextAction.
    var empty = childNode.onNextAction(selector)

    // If there's a frame request, then some state changed, which is equivalent to the traditional
    // case of a WorkflowAction being enqueued that just modifies state.
    empty = empty && !molecule.needsRecomposition
    with(selector) {
      recomposeChannel.onReceive(processRecompositionRequestFromChannel)
    }

    return empty
  }
}
