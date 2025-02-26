package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

internal class WorkflowComposableNode<
  OutputT,
  RenderingT,
  ParentPropsT,
  ParentStateT,
  ParentOutputT
  >(
  val workflowKey: String,
  private var handler: (OutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>,
  private val requestRerender: () -> Unit,
  private val sendAction: (WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>) -> Unit,
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : InlineListNode<WorkflowComposableNode<*, *, *, *, *>>, MonotonicFrameClock {

  companion object {
    private fun log(message: String) = message.lines().forEach {
      println("WorkflowComposableNode $it")
    }
  }

  override var nextListNode: WorkflowComposableNode<*, *, *, *, *>? = null

  private val coroutineContext = coroutineContext + this
  private val recomposer: Recomposer = Recomposer(coroutineContext)
  private val composition: Composition = Composition(UnitApplier, recomposer)
  private val rendering = mutableStateOf<RenderingT?>(null)
  private var frameRequest: FrameRequest<*>? = null
  private var frameTimeCounter = 0L
  private val emitOutput: (OutputT) -> Unit = { output ->
    // TODO set flag in WFNode saying that it will re-render imminently.

    // Allow the function calling this one to finish doing any state updates before triggering
    // a rerender.
    post {
      // Ensure any state updates performed by the caller get to invalidate any compositions that
      // read them. If the dispatcher is Main.immediate, this will synchronously call
      // withFrameNanos, so that needs to check the flag we set above.
      Snapshot.sendApplyNotifications()
      // If dispatcher is Main.immediate this will synchronously perform re-render.
      sendAction(handler(output))
    }
  }

  private fun post(action: () -> Unit) {
    CoroutineScope(coroutineContext).launch {
      val dispatcher = coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
      if (dispatcher?.isDispatchNeeded(coroutineContext) != true) {
        // TODO verify this actually posts to the main thread on Main.immediate
        yield()
      }
      action()
    }
  }

  fun start() {
    // TODO I think we need more than a simple UNDISPATCHED start to make this work – we have to
    //  pump the dispatcher until the composition is finished.
    CoroutineScope(coroutineContext).launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        log("runRecomposeAndApplyChanges")
        recomposer.runRecomposeAndApplyChanges()
      } finally {
        composition.dispose()
      }
    }
  }

  fun dispose() {
    recomposer.cancel()
  }

  /**
   * Updates the handler function that will be invoked by [acceptChildOutput].
   */
  fun <CO, CP, S, O> setHandler(newHandler: (CO) -> WorkflowAction<CP, S, O>) {
    @Suppress("UNCHECKED_CAST")
    handler = newHandler as (OutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>
  }

  /**
   * Has a separate type parameter to allow type erasure.
   */
  fun <O, R> render(content: @Composable (emitOutput: (O) -> Unit) -> R): R {
    log("render setting content")
    log(RuntimeException().stackTraceToString())
    composition.setContent {
      @Suppress("UNCHECKED_CAST")
      rendering.value = content(emitOutput as (O) -> Unit) as RenderingT
    }

    val frameRequest = this.frameRequest
    if (frameRequest != null) {
      this.frameRequest = null
      val frameTime = frameTimeCounter++
      log("render executing frame with time $frameTime")
      frameRequest.execute(frameTime)
      log("render finished executing frame with time $frameTime")
    } else {
      log(
        "render no frame request, skipping recomposition " +
          "(hasInvalidations=${composition.hasInvalidations})"
      )
    }

    log("render returning value: ${rendering.value}")
    @Suppress("UNCHECKED_CAST")
    return rendering.value as R
  }

  /**
   * Wrapper around [handler] that allows calling it with erased types.
   */
  @Suppress("UNCHECKED_CAST")
  fun acceptChildOutput(output: Any?): WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT> =
    handler(output as OutputT)

  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    check(frameRequest == null) { "Frame already requested" }
    log("withFrameNanos")
    log(RuntimeException().stackTraceToString())
    return suspendCancellableCoroutine { continuation ->
      frameRequest = FrameRequest(
        onFrame = onFrame,
        continuation = continuation
      )
      requestRerender()
    }
  }
}

private class FrameRequest<R>(
  private val onFrame: (frameTimeNanos: Long) -> R,
  private val continuation: CancellableContinuation<R>
) {
  fun execute(frameTimeNanos: Long) {
    val result = onFrame(frameTimeNanos)
    continuation.resume(result)
  }
}
