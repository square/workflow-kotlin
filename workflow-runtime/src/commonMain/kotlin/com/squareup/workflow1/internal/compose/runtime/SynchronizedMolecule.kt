package com.squareup.workflow1.internal.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.NullableInitBox
import com.squareup.workflow1.internal.Lock
import com.squareup.workflow1.internal.WorkStealingDispatcher
import com.squareup.workflow1.internal.withLock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.Volatile

/**
 * Creates a [launchSynchronizedMolecule] that will run its recomposition loop and effects in
 * this [CoroutineScope]. [onNeedsRecomposition] must ensure that
 * [SynchronizedMolecule.recomposeWithContent] is eventually called.
 *
 * See [SynchronizedMolecule] for more information.
 */
// TODO annotate internal, or pull out
public fun CoroutineScope.launchSynchronizedMolecule(
  onNeedsRecomposition: () -> Unit
): SynchronizedMolecule = RealSynchronizedMolecule(
  scope = this,
  onNeedsRecomposition = onNeedsRecomposition,
)

/**
 * A self-contained Compose runtime (like Molecule) that is driven by explicitly telling it when to
 * recompose.
 *
 * ## Usage
 *
 * Create an instance of this interface by calling [launchSynchronizedMolecule] and passing the
 * [CoroutineScope] used to run recomposition and effects, as well as a function to schedule
 * a call to [recomposeWithContent] when composition state is changed. When you're ready to
 * compose the initial content, call [recomposeWithContent]: this will compose the composable passed
 * to it and the compose runtime will start observing state changes. When state is changed, the
 * scheduling function passed to [launchSynchronizedMolecule] will be called, but the composition
 * will not be recomposed until [recomposeWithContent] is called again. The scheduling function will
 * never be called more than once before the next call to [recomposeWithContent].
 *
 * To check if composition state has changed imperatively, check [needsRecomposition].
 *
 * To stop observing state changes, either cancel the [CoroutineScope] or call
 * [SynchronizedMolecule.close].
 *
 * ## Implementation and runtime behavior
 *
 * The compose runtime is driven by an instance of [Recomposer] that runs the recomposition loop
 * in a coroutine, scheduling recompositions via a [MonotonicFrameClock]. When compose snapshot
 * state is changed, the recomposer eventually gets notified about the changes, which resumes an
 * internal suspending loop, and eventually requests a frame from its frame clock.
 *
 * This class helps out by collapsing some of those steps: As soon as snapshot apply notifications
 * have been sent this class will immediately make the frame request available, even if the
 * underlying dispatcher wouldn't have normally resumed the recompose loop in time to make the frame
 * request. It does this by running the recompose loop on a special dispatcher that wraps the
 * underlying dispatcher but allows us to explicitly advance for the recompose loop, and by
 * advancing it any time the recomposer reports pending work but hasn't
 * requested a frame yet.
 */
// TODO annotate internal, or pull out
public interface SynchronizedMolecule {

  /**
   * Returns true if the last composable passed to [recomposeWithContent] needs to be recomposed
   * due to some state that it read being changed.
   *
   * May start returning true before `onNeedsRecomposition` is called if the underlying dispatcher
   * hasn't had a chance to request a frame yet.
   */
  val needsRecomposition: Boolean

  /**
   * Performs a recomposition with the given [content] and returns its result.
   */
  fun <R> recomposeWithContent(content: @Composable () -> R): R

  /**
   * Stop observing composition state (calling `onNeedsRecomposition`). After calling this, it is
   * an error to call [recomposeWithContent] again, and [needsRecomposition] will always return
   * false.
   *
   * You don't need to call this if the coroutine scope is cancelled.
   */
  fun close()
}

private class RealSynchronizedMolecule(
  private val scope: CoroutineScope,
  private val onNeedsRecomposition: () -> Unit,
) : SynchronizedMolecule, MonotonicFrameClock {

  init {
    GlobalSnapshotManager.ensureStarted()
  }

  /**
   * It's fine to run the recompose loop on Unconfined since it's thread-safe internally, and
   * the only threading guarantee we care about is that the frame callback is executed during
   * the render pass, which we already control and doesn't depend on what dispatcher is used.
   */
  private val dispatcher = WorkStealingDispatcher(Dispatchers.Unconfined)
  private val recomposer: Recomposer = Recomposer(
    effectCoroutineContext = scope.coroutineContext
  )
  private val composition: Composition = Composition(UnitApplier, recomposer)
  private var content: (@Composable () -> Any?)? by mutableStateOf(null)
  private var lastResult: NullableInitBox<Any?> = NullableInitBox()

  /** Used to synchronize access to [frameRequest]. */
  private val lock = Lock()

  @Volatile
  private var frameRequest: FrameRequest<*>? = null

  /**
   * Used to stop [withFrameNanos] from calling [onNeedsRecomposition] when the frame is being
   * requested inside of [recomposeWithContent].
   */
  // TODO this should be a ThreadLocal since withFrameNanos can be called from any thread but
  //  it should only be true from the thread calling recomposeWithContent.
  @Volatile
  private var recomposing = false

  override val needsRecomposition: Boolean
    get() {
      if (frameRequest == null && recomposer.hasPendingWork) {
        // Allow the recompose loop to run and maybe request a frame.
        dispatcher.advanceUntilIdle()
      }
      return frameRequest != null
    }

  init {
    // setContent will synchronously perform the first recomposition before returning, which is why
    // we leave contentAfterInitial null for now: we don't want it to be called until we're actually
    // inside tryPerformRecompose.
    // We also need to set the composition content before calling startComposition so it doesn't
    // need to suspend to wait for it.
    // contentAfterInitial isn't snapshot state but that's fine, since when the recomposer is
    // started it will always recompose, childNode will be non-null by then, and it will never
    // change again.
    composition.setContent {
      content?.let { content ->
        val result = content()
        SideEffect {
          this.lastResult = NullableInitBox(result)
        }
      }
    }
  }

  override fun <R> recomposeWithContent(content: @Composable () -> R): R {
    // Update content in a snapshot to ensure it is applied before we ask for a frame.
    // Snapshot.withMutableSnapshot {
    // TODO get rid of this entirely, only allow content to be specified in constructor. This is a
    //  useless state write for our one use case and slow.
    this.content = content
    // }
    Snapshot.sendApplyNotifications()

    if (!lastResult.isInitialized) {
      // Initial request kicks off the recompose loop. This should synchronously request a frame.
      launchComposition()
    }

    // Synchronously recompose any invalidated composables, if any, and update lastResult.
    val frameRequest = tryGetFrameRequest()
    if (frameRequest == null) {
      if (!lastResult.isInitialized) {
        error("Expected initial composition to synchronously request initial frame.")
      }
    } else {
      // Hard-code unchanging frame time since there's no actual frame time code shouldn't rely on
      // this value.
      val frameResult = frameRequest.execute(0L)

      // If the composition threw an exception, re-throw it ourselves now instead of waiting for the
      // scope to get it, since lastResult may have not been initialized in this case and we'd throw
      // below and get supppressed.
      frameResult.exceptionOrNull()?.let {
        throw RuntimeException("ComposeWorkflow composition threw an exception", it)
      }

      // If the composition threw an exception, we want it to cancel the coroutine scope before
      // getOrThrow below does so.
      dispatcher.advanceUntilIdle()
    }

    @Suppress("UNCHECKED_CAST")
    return lastResult.getOrThrow() as R
  }

  @OptIn(ExperimentalStdlibApi::class)
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    // println("compose workflow withFrameNanos (dispatcher=${currentCoroutineContext()[CoroutineDispatcher]})")
    // println(RuntimeException().stackTraceToString())

    return suspendCancellableCoroutine { continuation ->
      lock.withLock {
        if (frameRequest != null) error("Concurrent frame request")
        frameRequest = FrameRequest(
          onFrame = onFrame,
          continuation = continuation
        )
      }

      if (!recomposing) {
        onNeedsRecomposition()
      }
    }
  }

  override fun close() {
    recomposer.close()
  }

  private fun launchComposition() {
    val frameClock: MonotonicFrameClock = this

    // Launch as undispatched to ensure the composition has a chance to start before this method
    // returns, and so that the composition is always disposed even if our job is cancelled
    // immediately.
    scope.launch(
      // Note: This context is _only_ used for the actual recompose loop. Everything inside the
      // composition (rememberCoroutineScope, LaunchedEffects, etc) will NOT see these, and will see
      // only whatever context was passed into the Recomposer's constructor (plus the stuff it adds
      // to that context itself, like the BroadcastFrameClock).
      context = dispatcher + frameClock,
      start = UNDISPATCHED
    ) {
      try {
        recomposer.runRecomposeAndApplyChanges()
      } finally {
        composition.dispose()
      }
    }
  }

  /**
   * Returns a [FrameRequest] representing the next frame request if the recomposer has work to do,
   * otherwise returns null. This method is best-effort: It tries to poke the recomposer to request
   * a frame even if it hasn't had a chance to resume its recompose loop yet.
   *
   * The returned continuation should be resumed with the frame time in nanoseconds.
   */
  private fun tryGetFrameRequest(): FrameRequest<*>? {
    // Fast paths: A request was already enqueued, or…
    // frameRequestChannel.tryReceive().getOrNull()?.let { return it }
    consumeFrameRequest()?.let {
      return it
    }

    // …no request was enqueued, and the recomposer doesn't need one.
    if (!recomposer.hasPendingWork) {
      // The recomposer is waiting for work, so there won't be a frame request even if we advance
      // the dispatcher.
      return null
    }

    // Slow path: The recomposer is waiting for its recompose loop to be resumed so it can request
    // a frame, so let it do that.
    // Set recomposing to avoid calling onRecomposeNeeded if this advancing triggers withFrameNanos.
    recomposing = true
    dispatcher.advanceUntilIdle()
    recomposing = false

    // If there's still no request then the recomposer either didn't actually need to resume or it
    // did but decided not to request a frame, either way we've done all we can.
    // return frameRequestChannel.tryReceive().getOrNull()
    return consumeFrameRequest()
  }

  private fun consumeFrameRequest(): FrameRequest<*>? = lock.withLock {
    frameRequest?.also { frameRequest = null }
  }

  private class FrameRequest<R>(
    private val onFrame: (frameTimeNanos: Long) -> R,
    private val continuation: CancellableContinuation<R>
  ) {
    fun execute(frameTimeNanos: Long): Result<R> {
      val frameResult = runCatching { onFrame(frameTimeNanos) }
      continuation.resumeWith(frameResult)
      return frameResult
    }
  }
}
