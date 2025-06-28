package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import com.squareup.workflow1.internal.compose.coroutines.WorkStealingDispatcher
import com.squareup.workflow1.internal.compose.coroutines.requireSend
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation

/**
 * Wraps a [Recomposer] and allows driving it by manually sending frames.
 *
 * To use, first launch a coroutine and call [runRecomposeAndApplyChanges]. This will delegate to
 * [Recomposer.runRecomposeAndApplyChanges], but with a special frame clock and dispatcher. Once
 * the recompose loop is running, call you can use [needsRecompose] to check if the recomposer is
 * ready to recompose, or call [onAwaitFrameAvailable] inside a [select] to for one. When you're
 * ready to perform the recomposition, call [tryGetFrameRequest] and then resume the returned
 * [Continuation] with the frame time.
 *
 * [Recomposer] relies heavily on coroutines and operates in multiple phases. When compose snapshot
 * state is changed, the recomposer eventually gets notified about the changes. When it's notified,
 * if it was waiting for work, it resumes an internal suspending loop. When the loop resumes it
 * eventually requests a frame from its frame clock.
 *
 * This class helps out by collapsing some of those steps: As soon as snapshot apply notifications
 * have been sent this class will immediately make the frame request available, even if the
 * underlying dispatcher wouldn't have normally resumed the recompose loop in time to make the frame
 * request. It does this by using a special dispatcher that we can explicitly advance for the
 * recompose loop, and by advancing it any time the recomposer reports pending work but hasn't
 * requested a frame yet.
 */
interface RecomposerDriver {
  /**
   * Returns true if the recomposer is ready to recompose. When true, the next call to
   * [tryPerformRecompose] will succeed.
   *
   * Use [onAwaitFrameAvailable] to wait for this to be true.
   */
  val needsRecompose: Boolean

  suspend fun runRecomposeAndApplyChanges()

  /**
   * If the [Recomposer] is ready to recompose ([needsRecompose] is true), performs the
   * recomposition with the given frame time and returns true. Returns false if there is no work to
   * do.
   */
  fun tryPerformRecompose(frameTimeNanos: Long): Boolean

  /**
   * Registers with selector to resume when [needsRecompose] becomes true.
   */
  fun <R> onAwaitFrameAvailable(
    selector: SelectBuilder<R>,
    block: suspend () -> R
  )
}

internal fun RecomposerDriver(recomposer: Recomposer): RecomposerDriver =
  RealRecomposerDriver(
    recomposer,
    WorkStealingDispatcher(
      // It's fine to run the recompose loop on Unconfined since it's thread-safe internally, and
      // the only threading guarantee we care about is that the frame callback is executed during
      // the render pass, which we already control and doesn't depend on what dispatcher is used.
      Dispatchers.Unconfined
    )
  )

private class RealRecomposerDriver(
  private val recomposer: Recomposer,
  private val dispatcher: WorkStealingDispatcher,
) : RecomposerDriver, MonotonicFrameClock {

  private val frameRequestChannel = Channel<FrameRequest<*>>(capacity = 1)

  /**
   * Returns true if the recomposer is ready to recompose. When true, the next call to
   * [tryPerformRecompose] will succeed.
   *
   * Use [onAwaitFrameAvailable] to wait for this to be true.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override val needsRecompose: Boolean
    get() {
      val wasEmpty = frameRequestChannel.isEmpty
      if (wasEmpty && recomposer.hasPendingWork) {
        // Allow the recompose loop to run and maybe request a frame.
        dispatcher.advanceUntilIdle()
        // Then check again if it actually requested a frame.
        return !frameRequestChannel.isEmpty
      } else {
        // There was work or the recomposer is still idle, either way no need to check the channel
        // again.
        return !wasEmpty
      }
    }

  override suspend fun runRecomposeAndApplyChanges() {
    // Note: This context is _only_ used for the actual recompose loop. Everything inside the
    // composition (rememberCoroutineScope, LaunchedEffects, etc) will NOT see these, and will see
    // only whatever context was passed into the Recomposer's constructor (plus the stuff it adds
    // to that context itself, like the BroadcastFrameClock).
    val frameClock: MonotonicFrameClock = this
    withContext(dispatcher + frameClock) {
      recomposer.runRecomposeAndApplyChanges()
    }
  }

  /**
   * If the [Recomposer] is ready to recompose ([needsRecompose] is true), performs the
   * recomposition with the given frame time and returns true. Returns false if there is no work to
   * do.
   */
  override fun tryPerformRecompose(frameTimeNanos: Long): Boolean {
    tryGetFrameRequest()?.let { frameRequest ->
      frameRequest.execute(frameTimeNanos)
      return true
    }
    return false
  }

  /**
   * Registers with selector to resume when [needsRecompose] becomes true.
   */
  override fun <R> onAwaitFrameAvailable(
    selector: SelectBuilder<R>,
    block: suspend () -> R
  ) {
    with(selector) {
      frameRequestChannel.onReceive { request ->
        // Re-enqueue the request so it will be picked up by the next call to tryGetFrameRequest.
        // We could use a separate property for this instead of the channel, but this keeps a single
        // source of truth. There's no race condition with other frames being sent unless
        // runRecomposeAndApplyChanges is called multiple times, since the compose runtime loop only
        // calls withFrameNanos from a single coroutine, so if there's a frame then we know that
        // coroutine is suspended and can't call it again.
        frameRequestChannel.requireSend(request)
        block()
      }
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    log("compose workflow withFrameNanos (dispatcher=${currentCoroutineContext()[CoroutineDispatcher]})")
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

  /**
   * Returns a [Continuation] representing the next frame request if the recomposer has work to do,
   * otherwise returns null. This method is best-effort: It tries to poke the recomposer to request
   * a frame even if it hasn't had a chance to resume its recompose loop yet.
   *
   * The returned continuation should be resumed with the frame time in nanoseconds.
   */
  private fun tryGetFrameRequest(): FrameRequest<*>? {
    // Fast paths: A request was already enqueued, or…
    frameRequestChannel.tryReceive().getOrNull()?.let { return it }

    // …no request was enqueued, and the recomposer doesn't need one.
    if (!recomposer.hasPendingWork) {
      // The recomposer is waiting for work, so there won't be a frame request even if we advance
      // the dispatcher.
      return null
    }

    // Slow path: The recomposer is waiting for its recompose loop to be resumed so it can request
    // a frame, so let it do that.
    dispatcher.advanceUntilIdle()

    // If there's still no request then the recomposer either didn't actually need to resume or it
    // did but decided not to request a frame, either way we've done all we can.
    return frameRequestChannel.tryReceive().getOrNull()
  }

  private class FrameRequest<R>(
    private val onFrame: (frameTimeNanos: Long) -> R,
    private val continuation: CancellableContinuation<R>
  ) {
    fun execute(frameTimeNanos: Long) {
      val frameResult = runCatching { onFrame(frameTimeNanos) }
      continuation.resumeWith(frameResult)
    }
  }
}
