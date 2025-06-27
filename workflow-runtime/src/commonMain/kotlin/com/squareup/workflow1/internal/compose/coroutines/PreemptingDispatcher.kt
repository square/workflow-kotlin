package com.squareup.workflow1.internal.compose.coroutines

import androidx.compose.runtime.collection.MutableVector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.ATOMIC
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

private val lock = Lock()

/**
 * A [CoroutineDispatcher] that delegates to another dispatcher but allows preempting any work
 * scheduled on this dispatcher by calling [advanceUntilIdle].
 *
 * E.g.
 * ```
 * val dispatcher = PreemptingDispatcher(Dispatchers.Main.immediate)
 * launch(dispatcher) {
 *   while (true) {
 *     awaitWorkAvailable()
 *     doWork()
 *   }
 * }
 * …
 * dispatcher.advanceUntilIdle()
 * ```
 *
 * Implementation: Whenever [dispatch] is called it adds the block to an internal queue.
 * When [dispatch] is called for the first time, a coroutine is launched into the dispatch context
 * with the [delegate] dispatcher that loops for as long as the job is alive and just reads items
 * out of an internal queue and executes them. When there are no more items enqueued, the loop
 * suspends and stores its continuation. When [dispatch] is called the second time, it sees that the
 * loop is already running. After enqueuing the block it checks for a continuation – if it finds
 * one, it resumes it, which resumes the loop to "dispatch" the block using whatever mechanism the
 * [delegate] dispatcher is using. If the dispatch context that the loop was launched on is
 * cancelled, then any remaining queued work is ran before finalizing cancellation, and the next
 * call to [dispatch] will cause a new dispatch coroutine to be launched.
 *
 * Alternatively, we could just launch a coroutine every time [dispatch] is called that would be
 * responsible for only executing that one block. This implementation tries to save some work by
 * just letting the coroutine run as long as possible and only _resuming a continuation_ on dispatch
 * instead of launching a whole new coroutine every time. We also can't just call [dispatch] on
 * [delegate] directly since that is illegal if the delegate doesn't
 * [require dispatch][CoroutineDispatcher.isDispatchNeeded] and in that case the coroutine runtime
 * will internally either resume the continuation synchronously or dispatch it to the threadlocal
 * event queue.
 *
 * It also complies with the
 * [dispatch] contract that the block _must always_ be ran eventually, without requiring this
 * dispatcher instance to have an explicit lifetime. It might be better to just give it a lifetime
 * though, especially since it's only used for a specific thing where the lifetime is clearly
 * defined.
 */
internal class PreemptingDispatcher(
  private val delegate: CoroutineDispatcher
) : CoroutineDispatcher() {

  // Access to these properties must always be synchronized with lock.
  private var queue: MutableVector<Runnable>? = null
  private var continuation: /*Cancellable*/Continuation<MutableVector<Runnable>?>? = null
  private var dispatchLoopRunning = false

  /**
   * Always returns true since we always need to track what work is waiting so we can advance it.
   */
  override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {
    var startDispatch = false
    val dispatchContinuation = lock.withLock {
      // Use a small queue capacity since in most cases the queue should be processed very soon
      // after it's created.
      val queue = queue ?: MutableVector<Runnable>(capacity = 3).also { queue = it }
      queue += block

      // If dispatchLoopRunning is true, then whatever we enqueued inside the lock is guaranteed to
      // be processed by a previously-launched loop, even if its job has already been cancelled.
      if (!dispatchLoopRunning) {
        // This is the first dispatch call, we need to start our
        dispatchLoopRunning = true
        startDispatch = true
      }

      this.continuation?.also {
        this.continuation = null
      }
    }

    // If we haven't launched the dispatch loop yet, do it outside the critical section since it
    // will synchronously grab the lock.
    if (startDispatch) {
      // This is guaranteed to at least process the runnable we just enqueued, even if context is
      // already cancelled.
      launchDispatchLoop(CoroutineScope(context))
    }

    // Trampoline the dispatch outside the critical section to avoid deadlocks.
    // This may fail if the loop job is cancelled but hasn't ran its finally block yet, but that's
    // fine because then the queue will be handled by the finally block.
    dispatchContinuation?.resume(null)
  }

  fun advanceUntilIdle() {
    val queueToDrain = lock.withLock { consumeQueueLocked() }

    // Drain the queue outside the critical section to ensure we don't deadlock if any of the
    // runnables try to dispatch.
    queueToDrain?.drainQueue()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun launchDispatchLoop(scope: CoroutineScope) {
    scope.launch(context = delegate, start = ATOMIC) {
      var queueToDrain: MutableVector<Runnable>? = null
      try {
        // On the first iteration of this loop there will always be work immediately available
        // since it was just enqueued by dispatch before launching the loop.
        queueToDrain = lock.withLock { consumeQueueLocked() }

        while (true) {
          queueToDrain?.drainQueue()

          // New work might have been enqueued while we were draining, so check again to avoid
          // suspending unless we absolutely have to.
          queueToDrain = lock.withLock { consumeQueueLocked() }

          // No work was available, so we need to suspend and wait for some.
          // If there was work available, immediately loop around and process it without suspending.
          if (queueToDrain == null) {
            // suspendCancellableCoroutine { continuation ->
            //   val continuationToResume: Continuation<Unit>? = lock.withLock {
            //     // Work may have been enqueued since we last checked before stashing our
            //     // continuation, so check one more time.
            //     queueToDrain = consumeQueueLocked()
            //     if (queueToDrain == null) {
            //       // No work was available, suspend.
            //       this@PreemptingDispatcher.continuation = continuation
            //       null
            //     } else {
            //       // Work was enqueued before we got the lock, so we can continue the loop
            //       // immediately to drain it.
            //       continuation
            //     }
            //   }
            //   // If work was available, dispatch immediately to start processing it.
            //   // This may fail, if the job was cancelled. In that case, queueToDrain will be
            //   // processed inside the finally below.
            //   continuationToResume?.resume(Unit)
            // }

            // TODO How does this handle cancellation? If it doesn't, maybe just better to use
            //  suspendCancellableCoroutine.
            queueToDrain = suspendCoroutineUninterceptedOrReturn { continuation ->
              // Work may have been enqueued since we last checked before stashing our
              // continuation, so check one more time.
              lock.withLock {
                val queue = consumeQueueLocked()
                if (queue == null) {
                  // No work was available, suspend.
                  this@PreemptingDispatcher.continuation = continuation.intercepted()
                  COROUTINE_SUSPENDED
                } else {
                  // There was work, so we don't need to actually suspend and can just continue
                  // executing immediately.
                  queue
                }
              }
            }

            if (queueToDrain == null) {
              // We were resumed after waiting for work, now there's work available so grab it.
              queueToDrain = lock.withLock { consumeQueueLocked() }
            }
          }
        }
      } finally {
        // We might have retrieved a queue while in the lock above but gotten cancelled before
        // resuming, so if this isn't null then drain it now.
        queueToDrain?.drainQueue()

        // Work might have been queued up while we were being cancelled, or processing the queue, so
        // grab the queue one last time while we publish that we're no longer running, and process
        // any leftover work while we're on the delegate dispatcher.
        queueToDrain = lock.withLock {
          dispatchLoopRunning = false
          continuation = null
          consumeQueueLocked()
        }
        queueToDrain?.drainQueue()
      }
    }
  }

  private fun consumeQueueLocked() = queue?.takeIf { it.isNotEmpty() }?.also { queue = null }

  private fun MutableVector<Runnable>.drainQueue() {
    forEach {
      it.run()
    }
  }
}
