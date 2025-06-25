package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineStart.ATOMIC
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext

internal const val OUTPUT_QUEUE_LIMIT = 1_000

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
),
  MonotonicFrameClock {

  companion object {
    init {
      GlobalSnapshotManager.ensureStarted()
    }

    private fun log(message: String) = message.lines().forEach {
      println("WorkflowComposableNode $it")
    }
  }

  private val recomposer: Recomposer = Recomposer(coroutineContext)
  private val composition: Composition = Composition(UnitApplier, recomposer)
  private var frameTimeCounter = 0L
  private val frameRequestChannel = Channel<FrameRequest<*>>(capacity = 1)
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
  private val processFrameRequestFromChannel: (FrameRequest<*>) -> ActionProcessingResult =
    { frameRequest ->
      log("frame request received from channel: $frameRequest")

      // Re-enqueue the request so it will be picked up in the imminent render pass. We could use a
      // separate property for this instead of the channel, but this keeps a single source of truth.
      // This enqueuing will never be seen by onNextAction since the render pass will run before
      // that.
      frameRequestChannel.requireSend(frameRequest)

      // A pure frame request means compose state was updated that the composition read, but
      // emitOutput was not called, so we don't have any outputs to report.
      val applied = ActionApplied<OutputT>(
        output = null,
        stateChanged = composition.hasInvalidations
      )

      // Propagate the action up the workflow tree.
      log("sending no output to parent: $applied")
      emitAppliedActionToParent(applied)
    }

  init {
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
        // ComposeWorkflowChildNode can't tell if its own state changed since that information about
        // specific composables/recompose scopes is only visible inside the compose runtime, so
        // individual ComposeWorkflow nodes always report no state changes (unless they have a
        // traditional child that reported a state change).
        // However, we *can* check if any state changed that was read by anything in the
        // composition, so when an action bubbles up to here, the top of the composition, we use
        // that information to set the state changed flag if necessary.
        val aggregateAction = if (composition.hasInvalidations && !actionApplied.stateChanged) {
          actionApplied.copy(stateChanged = true)
        } else {
          actionApplied
        }
        emitAppliedActionToParent(aggregateAction)

        // TODO it's possible that after the action cascade, composition state changed but the
        //  compose runtime hasn't had a chance to request a frame yet (i.e. if dispatcher is not
        //  immediate). When that happens, there will be no frame for the render pass triggered by
        //  the cascade, so that pass will be a noop for this subtree of compose workflows, and then
        //  there will be a separate render pass triggered just for it. This is inefficient, but
        //  there's no way to yield to the compose runtime in the middle of an action cascade. We
        //  probably need to force runRecomposeAndApplyChanges to run undispatched.
        //  I did have a workaround for the case where an output was emitted into the channel, since
        //  selector handlers can suspend, but that doesn't work for the case where the original
        //  action cames from a traditional workflow. That code was:
        // if (compositionInvalidated && frameRequestChannel.isEmpty) {
        //   // The composition needs to recompose but the recompose loop running in startComposition
        //   // hasn't had a chance to call withFrameNanos yet. Don't return control to the workflow
        //   // runtime until it has — effectively, "yield" to the compose runtime.
        //   // Otherwise we could end up doing a no-op render pass followed by an additional render pass.
        //   // This should only happen when using a non-immediate dispatcher.
        //   //
        //   // Note 1: We do the check-and-yield after the walk up and down the workflow tree
        //   // intentionally. If there are multiple ComposeWorkflows in the tree, they could invalidate
        //   // themselves during the action cascade and call sendApplyNotifications after our call above.
        //   // However, since our call was first, it will have scheduled our compose runtime before
        //   // theirs. By doing the check after this potential situation, the top-most ComposeWorkflowNode
        //   // (CWN) will yield to its compose runtime first, but everything below it will have been
        //   // scheduled first, so a single suspending receive call allows all compose runtimes in the
        //   // tree to make their frame requests before resuming the walk back down the tree on return.
        //   // Lower-level CWNs will then hit the fast path of !frameRequestChannel.isEmpty and avoid
        //   // suspending themselves.
        //   //
        //   // Note 2: This looks like a race condition, but it's not because there is only one way for
        //   // a frame request to be enqueued (withFrameNanos) and there is only one code path calling
        //   // that (the recompose loop), which never makes concurrent requests. If a frame request has
        //   // been enqueued, it's impossible for another request to be made until we resume its
        //   // continuation.
        //   log(
        //     "composition invalidated after processing output cascade but no frame has been requested " +
        //       "yet, waiting for a request…"
        //   )
        //   val request = frameRequestChannel.receive()
        //   log("…a frame request has been received! now we can get on with rendering")
        //   frameRequestChannel.requireSend(request)
        // }
      }
    )
  }

  override fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT {
    log("render setting content")
    this.cachedComposeWorkflow = workflow as ComposeWorkflow
    this.lastProps = input

    val frameRequest = if (!lastRendering.isInitialized) {
      // Initial render kicks off the render loop. This should always synchronously request a frame.
      startComposition()

      frameRequestChannel.tryReceive().getOrElse { cause ->
        throw IllegalStateException(
          "Expected initial composition to synchronously request a frame.",
          cause
        )
      }
    } else {
      frameRequestChannel.tryReceive().getOrNull()
    }

    // Synchronously recompose any invalidated composables, if any, and update lastRendering.
    // It is very likely that frameRequest is null: any time the workflow runtime is doing a render
    // pass and no state read by our composition changed, there shouldn't be a frame request. Note
    // that if state is changed before emitting an output, our output handler will suspend until
    // the compose runtime makes the frame request to ensure it's ready for the subsequent render
    // pass.
    if (frameRequest != null) {
      log("renderFrame with time $frameTimeCounter")
      frameRequest.execute(frameTimeCounter)
      log("renderFrame finished executing frame with time $frameTimeCounter")
      frameTimeCounter++
    }

    if (!lastRendering.isInitialized) {
      // This is a defensive programming check – it should never be possible to hit this case
      // unless we have a bug.
      throw AssertionError(
        "Expected compose workflow rendering to have been initialized after first frame."
      )
    }
    return lastRendering.getOrThrow().also {
      log("render returning value: $it")
    }
  }

  override fun snapshot(): TreeSnapshot = childNode!!.snapshot()

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
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
    empty = empty && (frameRequestChannel.isEmpty || frameRequestChannel.isClosedForSend)
    with(selector) {
      frameRequestChannel.onReceive(processFrameRequestFromChannel)
    }

    return empty
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun startComposition() {
    val frameClock = this
    launch(context = frameClock, start = ATOMIC) {
      try {
        log("runRecomposeAndApplyChanges")
        recomposer.runRecomposeAndApplyChanges()
      } catch (e: Throwable) {
        log("compose runtime threw: $e\n" + e.stackTraceToString())
        ensureActive()
        throw e
      } finally {
        composition.dispose()
      }
    }
  }

  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    log("compose workflow withFrameNanos")
    log(RuntimeException().stackTraceToString())

    return suspendCancellableCoroutine { continuation ->
      val frameRequest = FrameRequest(
        onFrame = onFrame,
        continuation = continuation
      )

      // This will throw if a frame request is already enqueued. If currently in an action cascade
      // (i.e. handling a received output), then it will be picked up in the imminent re-render.
      // Otherwise, onNextAction will have registered a receiver for it that will trigger a render
      // pass.
      frameRequestChannel.requireSend(frameRequest)
    }
  }

  private data class FrameRequest<R>(
    private val onFrame: (frameTimeNanos: Long) -> R,
    private val continuation: CancellableContinuation<R>
  ) {
    fun execute(frameTimeNanos: Long) {
      val result = runCatching { onFrame(frameTimeNanos) }
      continuation.resumeWith(result)
    }
  }
}

// TODO pull into separate file
internal fun <T> Channel<T>.requireSend(element: T) {
  val result = trySend(element)
  if (result.isClosed) {
    throw IllegalStateException(
      "Tried emitting output to workflow whose output channel was closed.",
      result.exceptionOrNull()
    )
  }
  if (result.isFailure) {
    error("Tried emitting output to workflow whose output channel was full.")
  }
}
