package com.squareup.workflow1.internal

import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.NullableInitBox
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Sink
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import kotlin.reflect.KType

private const val OUTPUT_QUEUE_LIMIT = 1_000

@OptIn(WorkflowExperimentalApi::class)
internal class ComposeWorkflowNode<PropsT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  workflow: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  baseContext: CoroutineContext,
  // Providing default value so we don't need to specify in test.
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
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

  private var cachedComposeWorkflow: ComposeWorkflow<PropsT, OutputT, RenderingT>? by
  mutableStateOf(null)
  private var lastProps by mutableStateOf(initialProps)
  private var lastRendering = NullableInitBox<RenderingT>()
  private val recomposer: Recomposer = Recomposer(coroutineContext)
  private val composition: Composition = Composition(UnitApplier, recomposer)
  private var frameTimeCounter = 0L
  private val outputsChannel = Channel<OutputT>(capacity = OUTPUT_QUEUE_LIMIT)
  private val frameRequestChannel = Channel<FrameRequest<*>>(capacity = 1)

  /**
   * This is the lambda passed to every invocation of the [ComposeWorkflow.produceRendering] method.
   * It merely enqueues the output in the channel. The actual processing happens when the receiver
   * registered by [onNextAction] calls [processOutputFromChannel].
   */
  private val sendOutputToChannel: (OutputT) -> Unit = { output ->
    // TODO defer this work until some time very soon in the future, but after the immediate caller
    //  has returned. E.g. launch a coroutine, but make sure it runs before the next frame (compose
    //  or choreographer). This will ensure that if the caller sets state _after_ calling this
    //  method the state changes are consumed by the resulting recomposition.

    // If dispatcher is Main.immediate this will synchronously perform re-render.
    log("sending output to channel: $output")
    outputsChannel.requireSend(output)
  }

  /**
   * Function invoked when [onNextAction] receives an output from [outputsChannel].
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private val processOutputFromChannel: suspend (OutputT) -> ActionProcessingResult = { output ->
    log("output received from channel: $output")
    // Ensure any state updates performed by the output sender gets to invalidate any
    // compositions that read them, so we can check hasInvalidations below.
    // If no frame has been requested yet this will request a frame. If the dispatcher is
    // Main.immediate the frame will be requested synchronously.
    Snapshot.sendApplyNotifications()

    val compositionInvalidated = composition.hasInvalidations
    val applied = ActionApplied(
      output = WorkflowOutput(output),
      stateChanged = compositionInvalidated
    )

    // Invoke the parent's handler to propagate the output up the workflow tree.
    log("sending output to parent: $applied")
    val result = emitAppliedActionToParent(applied)

    if (compositionInvalidated && frameRequestChannel.isEmpty) {
      // The composition needs to recompose but the recompose loop running in startComposition
      // hasn't had a chance to call withFrameNanos yet. Don't return control to the workflow
      // runtime until it has — effectively, "yield" to the compose runtime.
      // Otherwise we could end up doing a no-op render pass followed by an additional render pass.
      // This should only happen when using a non-immediate dispatcher.
      //
      // Note 1: We do the check-and-yield after the walk up and down the workflow tree
      // intentionally. If there are multiple ComposeWorkflows in the tree, they could invalidate
      // themselves during the action cascade and call sendApplyNotifications after our call above.
      // However, since our call was first, it will have scheduled our compose runtime before
      // theirs. By doing the check after this potential situation, the top-most ComposeWorkflowNode
      // (CWN) will yield to its compose runtime first, but everything below it will have been
      // scheduled first, so a single suspending receive call allows all compose runtimes in the
      // tree to make their frame requests before resuming the walk back down the tree on return.
      // Lower-level CWNs will then hit the fast path of !frameRequestChannel.isEmpty and avoid
      // suspending themselves.
      //
      // Note 2: This looks like a race condition, but it's not because there is only one way for
      // a frame request to be enqueued (withFrameNanos) and there is only one code path calling
      // that (the recompose loop), which never makes concurrent requests. If a frame request has
      // been enqueued, it's impossible for another request to be made until we resume its
      // continuation.
      log(
        "composition invalidated after processing output cascade but no frame has been requested " +
          "yet, waiting for a request…"
      )
      val request = frameRequestChannel.receive()
      log("…a frame request has been received! now we can get on with rendering")
      frameRequestChannel.requireSend(request)
    }

    result
  }

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
    interceptor.onSessionStarted(workflowScope = this, session = this)
    // Don't care about this return value, our state is separate.
    interceptor.onInitialState(
      props = initialProps,
      snapshot = null, // TODO
      workflowScope = this,
      session = this,
      proceed = { _, _, _ -> ComposeWorkflowState }
    )

    // By not calling setContent directly every time, we ensure that if neither the workflow
    // instance nor input changed, we don't recompose.
    // setContent will synchronously perform the first recomposition before returning, which is why
    // we leave cachedComposeWorkflow null for now: we don't want its produceRendering to be called
    // until we're actually doing a render pass.
    // We also need to set the composition content before calling startComposition so it doesn't
    // need to suspend to wait for it.
    composition.setContent {
      @Suppress("NAME_SHADOWING")
      val workflow = cachedComposeWorkflow
      if (workflow != null) {
        val rendering = interceptor.onRenderComposeWorkflow(
          renderProps = lastProps,
          emitOutput = sendOutputToChannel,
          proceed = { innerProps, innerEmitOutput ->
            workflow.produceRendering(
              props = innerProps,
              emitOutput = innerEmitOutput
            )
          },
          session = this
        )

        // lastRendering isn't snapshot state, so wait until the composition is applied to update
        // it.
        SideEffect {
          lastRendering = NullableInitBox(rendering)
        }
      }
    }
    cachedComposeWorkflow = workflow
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

  override fun snapshot(): TreeSnapshot {
    return interceptor.onSnapshotStateWithChildren(
      session = this,
      proceed = {
        // Compose workflows do not support the onSnapshotState interceptor since they don't
        // distinguish between snapshot state objects for themselves and their child
        // ComposeWorkflows.
        // TODO Support snapshots from rememberSaveable.
        TreeSnapshot(workflowSnapshot = null, childTreeSnapshots = ::emptyMap)
      }
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  override fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean {
    val empty = outputsChannel.isEmpty || outputsChannel.isClosedForReceive

    with(selector) {
      // Selection is strongly-ordered: If multiple subjects become available simultaneously, then
      // the one whose receiver was registered first will fire first. We always want to handle
      // outputs first because the output handler will implicitly also handle frame requests. If a
      // frame request happens at the same time or the output handler enqueues a frame request,
      // then the subsequent render pass will dequeue the frame request itself before the next call
      // to onNextAction.
      outputsChannel.onReceive(processOutputFromChannel)
      frameRequestChannel.onReceive(processFrameRequestFromChannel)
    }

    return empty
  }

  override fun cancel(cause: CancellationException?) {
    // TODO Do we need to explicitly call this if we're cancelling the parent scope anyway?
    recomposer.cancel()
    super.cancel(cause)
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
}

/**
 * Fake state object passed to WorkflowInterceptors.
 */
private object ComposeWorkflowState

private class ComposeRenderContext<PropsT, OutputT>(
  override val runtimeConfig: RuntimeConfig,
  override val actionSink: Sink<WorkflowAction<PropsT, ComposeWorkflowState, OutputT>>,
  override val workflowTracer: WorkflowTracer?,
) : BaseRenderContext<PropsT, ComposeWorkflowState, OutputT> {

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    throw UnsupportedOperationException("runningSideEffect not supported in ComposeWorkflows")
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT {
    throw UnsupportedOperationException("remember not supported in ComposeWorkflows")
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, ComposeWorkflowState, OutputT>
  ): ChildRenderingT {
    throw UnsupportedOperationException("renderChild not supported in ComposeWorkflows")
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

private fun <T> Channel<T>.requireSend(element: T) {
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
