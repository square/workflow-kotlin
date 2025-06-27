package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.NullableInitBox
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
import com.squareup.workflow1.internal.UnitApplier
import com.squareup.workflow1.internal.WorkflowNodeId
import kotlinx.coroutines.CoroutineStart.ATOMIC
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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

  private val recomposer: Recomposer = Recomposer(coroutineContext)

  // TODO we could create a PreemptingDispatcher and stash it in the coroutine context (not as a
  //  dispatcher, but inside a private holder) so we can reuse it for all compose runtimes. But it
  //  wouldn't really save that much and might not be worth it.
  private val recomposerDriver = RecomposerDriver(recomposer = recomposer, scope = this)
  private val composition: Composition = Composition(UnitApplier, recomposer)

  private var frameTimeCounter = 0L

  private var cachedComposeWorkflow: ComposeWorkflow<PropsT, OutputT, RenderingT> by
  mutableStateOf(workflow)
  private var lastProps by mutableStateOf(initialProps)
  private var lastRendering = NullableInitBox<RenderingT>()

  /**
   * This is initialized to null so we don't render the workflow when initially calling
   * [composition.setContent]. It is then set, and never nulled out again.
   */
  private var childNode: ComposeWorkflowChildNode<PropsT, OutputT, RenderingT>? = null

  /**
   * Function invoked when [onNextAction] receives a frame request from [withFrameNanos].
   * This handles the case where some state read by the composition is changed but emitOutput is
   * not called.
   */
  private val processFrameRequestFromChannel: () -> ActionProcessingResult = {
    log("frame request received from channel")

    // A pure frame request means compose state was updated that the composition read, but
    // emitOutput was not called, so we don't have any outputs to report.
    val applied = ActionApplied<OutputT>(
      output = null,
      stateChanged = recomposerDriver.needsRecompose
    )

    // Propagate the action up the workflow tree.
    log("sending no output to parent: $applied")
    emitAppliedActionToParent(applied)
  }

  init {
    GlobalSnapshotManager.ensureStarted()

    // By not calling setContent directly every time, we ensure that if neither the workflow
    // instance nor input changed, we don't recompose.
    // setContent will synchronously perform the first recomposition before returning, which is why
    // we leave cachedComposeWorkflow null for now: we don't want its produceRendering to be called
    // until we're actually doing a render pass.
    // We also need to set the composition content before calling startComposition so it doesn't
    // need to suspend to wait for it.
    composition.setContent {
      val childNode = this.childNode
      if (childNode != null) {
        val rendering = childNode.produceRendering(
          workflow = cachedComposeWorkflow,
          props = lastProps
        )

        SideEffect {
          this.lastRendering = NullableInitBox(rendering)
        }
      }
    }

    childNode = ComposeWorkflowChildNode(
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
            "actionApplied=$actionApplied, needsRecompose=${recomposerDriver.needsRecompose})"
        )

        // ComposeWorkflowChildNode can't tell if its own state changed since that information about
        // specific composables/recompose scopes is only visible inside the compose runtime, so
        // individual ComposeWorkflow nodes always report no state changes (unless they have a
        // traditional child that reported a state change).
        // However, we *can* check if any state changed that was read by anything in the
        // composition, so when an action bubbles up to here, the top of the composition, we use
        // that information to set the state changed flag if necessary.
        val aggregateAction = if (recomposerDriver.needsRecompose && !actionApplied.stateChanged) {
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
  }

  override fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT {
    log("render setting props and workflow states")
    this.cachedComposeWorkflow = workflow as ComposeWorkflow
    this.lastProps = input

    // Ensure that recomposer has a chance to process any state changes from the action cascade that
    // triggered this render before we check for a frame.
    log("render sending apply notifications again needsRecompose=${recomposerDriver.needsRecompose}")
    Snapshot.sendApplyNotifications()
    log("sent apply notifications, needsRecompose=${recomposerDriver.needsRecompose}")

    val initialRender = !lastRendering.isInitialized
    if (initialRender) {
      // Initial render kicks off the render loop. This should always synchronously request a frame.
      startComposition()
    }

    // Synchronously recompose any invalidated composables, if any, and update lastRendering.
    // It is very likely that trySendFrame will fail: any time the workflow runtime is doing a
    // render pass and no state read by our composition changed, there shouldn't be a frame request.
    log("renderFrame with time $frameTimeCounter")
    val frameSent = recomposerDriver.tryPerformRecompose(frameTimeCounter)
    if (frameSent) {
      log("renderFrame finished executing frame with time $frameTimeCounter")
      frameTimeCounter++
    } else {
      log("no frame request at time of render!")
      if (initialRender) {
        error("Expected initial composition to synchronously request initial frame.")
      }
    }

    return lastRendering.getOrThrow().also {
      log("render returning value: $it")
    }
  }

  override fun snapshot(): TreeSnapshot = childNode!!.snapshot()

  override fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean {
    // We must register for child actions before frame requests, because selection is
    // strongly-ordered: If multiple subjects become available simultaneously, then the one whose
    // receiver was registered first will fire first. We always want to handle outputs first because
    // the output handler will implicitly also handle frame requests. If a frame request happens at
    // the same time or the output handler enqueues a frame request, then the subsequent render pass
    // will dequeue the frame request itself before the next call to onNextAction.
    var empty = childNode!!.onNextAction(selector)

    // If there's a frame request, then some state changed, which is equivalent to the traditional
    // case of a WorkflowAction being enqueued that just modifies state.
    empty = empty && !recomposerDriver.needsRecompose
    recomposerDriver.onAwaitFrameAvailable(selector, processFrameRequestFromChannel)

    return empty
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun startComposition() {
    launch(start = ATOMIC) {
      try {
        log("runRecomposeAndApplyChanges")
        recomposerDriver.runRecomposeAndApplyChanges()
      } catch (e: Throwable) {
        log("compose runtime threw: $e\n" + e.stackTraceToString())
        ensureActive()
        throw e
      } finally {
        composition.dispose()
      }
    }
  }
}
