package com.squareup.workflow1.internal.compose.coroutines

import androidx.compose.runtime.collection.MutableVector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.concurrent.Volatile
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

private val lock = Lock()

/**
 * A [CoroutineDispatcher] that delegates to another dispatcher but allows stealing any work
 * scheduled on this dispatcher and performing it synchronously by calling [advanceUntilIdle].
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
 * @param delegate The [CoroutineDispatcher] or other [ContinuationInterceptor] to delegate
 * scheduling behavior to. This can either be a confined or unconfined dispatcher, and its behavior
 * will be preserved transparently.
 */
internal class WorkStealingDispatcher(
  private val delegate: ContinuationInterceptor
) : CoroutineDispatcher() {

  // Access to these properties must always be synchronized with lock.
  /**
   * The queue of unconsumed work items. When there is no contention on the dispatcher, only one
   * queue will ever be allocated. Only when [dispatch] is called while the queue is being processed
   * (either by [advanceUntilIdle] or a [DispatchContinuation]) then a new queue will be allocated,
   * but when the processing is done the old one will be placed back here to be re-used.
   */
  @Volatile
  private var queue: MutableVector<Runnable>? = null

  @Volatile
  private var dispatchScheduled = false

  /**
   * Cached [DispatchContinuation] used to delegate to the [delegate]'s dispatching behavior from
   * [dispatch]. This is initialized the first call to [dispatch] that needs dispatch, and then
   * never changed.
   */
  @Volatile
  private var dispatchContinuation: DispatchContinuation? = null
  // End of synchronized properties.

  /**
   * Always returns true since we always need to track what work is waiting so we can advance it.
   */
  override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {
    var continuation: DispatchContinuation? = null
    lock.withLock {
      // Use a small queue capacity since in most cases the queue should be processed very soon
      // after it's created.
      val queue = queue ?: MutableVector<Runnable>(capacity = 3).also { this.queue = it }
      queue += block

      // If no dispatch is currently scheduled, then flag that we're handling it, and schedule one
      // outside the critical section.
      if (!dispatchScheduled) {
        dispatchScheduled = true
        continuation = dispatchContinuation ?: DispatchContinuation()
          .also { dispatchContinuation = it }
      }
    }

    // Trampoline the dispatch outside the critical section to avoid deadlocks.
    // This will either synchronously run block or dispatch it, depending on what resuming a
    // continuation on the delegate dispatcher would do.
    continuation?.resumeOnDelegateDispatcher()
  }

  /**
   * "Steals" work that was scheduled on this dispatcher but hasn't had a chance to run yet until
   * there is no work left to do.
   */
  fun advanceUntilIdle() {
    var wasDispatchScheduled = false
    advanceUntilIdle(
      onStartLocked = {
        // If no dispatch was scheduled, then set the flag so that any new dispatch calls that
        // happen while we're draining the queue won't schedule one unnecessarily since we'll just
        // handle them.
        // Note that we could "cancel" the dispatch if this is true here, since we're stealing all
        // its work, but we can't cancel that task so it will just have to noop.
        wasDispatchScheduled = dispatchScheduled.also {
          if (!it) dispatchScheduled = true
        }
      },
      onFinishedLocked = {
        // If we set this flag above, then clear it now so future dispatch calls schedule normally.
        dispatchScheduled = wasDispatchScheduled
      })
  }

  /**
   * Executes queued work items until there are none left, then returns.
   *
   * @param onStartLocked Called while [lock] is held exactly 1 time before any tasks are executed.
   * @param onFinishedLocked Called while [lock] is held exactly 1 time after all tasks are finished
   * executing.
   */
  private inline fun advanceUntilIdle(
    onStartLocked: () -> Unit = {},
    onFinishedLocked: () -> Unit
  ) {
    var queueToDrain: MutableVector<Runnable>? = null
    do {
      queueToDrain = lock.withLock {
        // Will only be null on first loop, since if it's null after this critical section we'll
        // exit the loop.
        if (queueToDrain == null) {
          onStartLocked()
        }

        // We're about to overwrite queueToDrain, so put the old one back so future calls to
        // dispatch might not need to allocate a new queue.
        consumeQueueLocked(queueToRecycle = queueToDrain).also {
          if (it == null) {
            onFinishedLocked()
          }
        }
      }

      // Drain the queue outside the critical section to ensure we don't deadlock if any of the
      // runnables try to dispatch.
      queueToDrain?.drainQueue()
    } while (queueToDrain != null)
  }

  /**
   * If there are work items queued up, returns the queue, otherwise returns null. MUST ONLY BE
   * CALLED while [lock] is held.
   *
   * If [queueToRecycle] is non-null then we try to place it back in the [queue] property for the
   * next call to [dispatch] (after clearing it) so it won't have to allocate a new one. After this
   * method returns [queueToRecycle] is unsafe to use for the calling code since it might be
   * modified by another thread.
   */
  private fun consumeQueueLocked(
    queueToRecycle: MutableVector<Runnable>?
  ): MutableVector<Runnable>? {
    if (queueToRecycle != null && queueToRecycle === queue) {
      throw IllegalArgumentException("Cannot recycle queue with itself")
    }

    return when {
      queue == null -> {
        // The next dispatch would allocate a new queue, so recycle one if possible.
        queue = queueToRecycle?.apply { clear() }
        null
      }

      queue!!.isEmpty() -> {
        // The next dispatch call already has a queue to use, so just let the recycled one be GC'd
        // and don't bother clearing it.
        null
      }

      else -> {
        queue.also {
          queue = queueToRecycle?.apply { clear() }
        }
      }
    }
  }

  private fun MutableVector<Runnable>.drainQueue() {
    forEach {
      it.run()
    }
  }

  /**
   * A reusable continuation that is used to access the coroutine runtime's resumption behavior for
   * both confined and unconfined dispatchers. See [resumeOnDelegateDispatcher] for more information
   * on how this works.
   *
   * [WorkStealingDispatcher] guarantees that only one instance of this class will be created per
   * dispatcher, and that it will never be resumed more than once concurrently, so it's safe to
   * reuse.
   */
  private inner class DispatchContinuation : Continuation<Unit> {
    override val context: CoroutineContext get() = delegate

    /**
     * Cache for intercepted coroutine so we can release it from [resumeWith].
     * [WorkStealingDispatcher] guarantees only one resume call will happen until the continuation
     * is done, so we don't need to guard this property with a lock.
     */
    private var intercepted: Continuation<Unit>? = null

    /**
     * Resumes this continuation on [delegate] by intercepting it and resuming the intercepted
     * continuation. When a dispatcher returns false from [isDispatchNeeded], then when
     * continuations intercepted by it are resumed, they may either be ran in-place or scheduled to
     * a special thread-local queue. The only way to access this queue is to have the dispatcher
     * intercept a contination and resume the intercepted continuation.
     */
    fun resumeOnDelegateDispatcher() {
      val intercepted = delegate.interceptContinuation(this).also {
        this.intercepted = it
      }

      // If delegate is a CoroutineDispatcher, intercepted will be a special Continuation that will
      // check the delegate's isDispatchNeeded to decide whether to call dispatch() or to enqueue it
      // to the thread-local unconfined queue.
      intercepted.resume(Unit)
    }

    /**
     * DO NOT CALL DIRECTLY! Call [resumeOnDelegateDispatcher] instead.
     */
    override fun resumeWith(result: Result<Unit>) {
      intercepted?.let {
        delegate.releaseInterceptedContinuation(it)
        intercepted = null
      }

      advanceUntilIdle(onFinishedLocked = {
        // Set this in the lock when we're about to return so that any dispatch calls waiting
        // on the lock will know to schedule a fresh dispatch.
        dispatchScheduled = false
      })
    }
  }
}
