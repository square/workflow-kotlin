package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.WorkStealingDispatcher.Companion.wrapDispatcherFrom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlin.concurrent.Volatile
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * A [CoroutineDispatcher] that delegates to another dispatcher but allows stealing any work
 * scheduled on this dispatcher and performing it synchronously by calling [advanceUntilIdle].
 *
 * The easiest way to create one is by calling [wrapDispatcherFrom].
 *
 * E.g.
 * ```
 * val dispatcher = WorkStealingDispatcher.wrapDispatcherFrom(scope.coroutineContext)
 * scope.launch(dispatcher) {
 *   while (true) {
 *     lots()
 *     of()
 *     suspending()
 *     calls()
 *   }
 * }
 * …
 * dispatcher.advanceUntilIdle()
 * ```
 *
 * @param delegateInterceptor The [CoroutineDispatcher] or other [ContinuationInterceptor] to
 * delegate scheduling behavior to. This can either be a confined or unconfined dispatcher, and its
 * behavior will be preserved transparently.
 */
public open class WorkStealingDispatcher protected constructor(
  private val delegateInterceptor: ContinuationInterceptor,
  lock: Lock?,
  queue: LinkedHashSet<DelegateDispatchedContinuation>?
) : CoroutineDispatcher() {
  companion object {
    /**
     * Creates a [WorkStealingDispatcher] that supports [Delay] if [delegateInterceptor] does.
     */
    operator fun invoke(delegateInterceptor: ContinuationInterceptor): WorkStealingDispatcher =
      createMatchingDelayability(
        delegateInterceptor = delegateInterceptor,
        lock = null,
        queue = null
      )

    /**
     * Returns a [WorkStealingDispatcher] that delegates to the [CoroutineDispatcher] from
     * [context]. If the context does not specify a dispatcher, [Dispatchers.Default] is used.
     */
    fun wrapDispatcherFrom(context: CoroutineContext): WorkStealingDispatcher {
      // If there's no dispatcher in the context then the coroutines runtime will fall back to
      // Dispatchers.Default anyway.
      val baseDispatcher = context[ContinuationInterceptor] ?: Dispatchers.Default
      return invoke(delegateInterceptor = baseDispatcher)
    }

    /**
     * Returns a [WorkStealingDispatcher] that either does or doesn't implement [Delay] depending
     * on whether [delegateInterceptor] implements it, by delegating to its implementation.
     */
    @OptIn(InternalCoroutinesApi::class)
    private fun createMatchingDelayability(
      delegateInterceptor: ContinuationInterceptor,
      lock: Lock?,
      queue: LinkedHashSet<DelegateDispatchedContinuation>?
    ): WorkStealingDispatcher {
      return if (delegateInterceptor is Delay) {
        DelayableWorkStealingDispatcher(
          delegate = delegateInterceptor,
          delay = delegateInterceptor,
          lock = lock,
          queue = queue
        )
      } else {
        WorkStealingDispatcher(
          delegateInterceptor = delegateInterceptor,
          lock = lock,
          queue = queue
        )
      }
    }
  }

  /** Used to synchronize access to the mutable properties of this class. */
  private val lock = lock ?: Lock()

  // region Access to these properties must always be synchronized with lock.
  private val queue = queue ?: LinkedHashSet()
  // endregion

  /**
   * Always returns true since we always need to track what work is waiting so we can advance it.
   */
  final override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

  final override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {
    val continuation = DelegateDispatchedContinuation(context, block)
    lock.withLock {
      queue += continuation
    }

    // Trampoline the dispatch outside the critical section to avoid deadlocks.
    // This will either synchronously run block or dispatch it, depending on what resuming a
    // continuation on the delegate dispatcher would do.
    continuation.resumeOnDelegateDispatcher()
  }

  /**
   * Calls [limitedParallelism] on [delegateInterceptor] and wraps the returned dispatcher with
   * a [WorkStealingDispatcher] that this instance will steal from.
   *
   * This satisfies the limited parallelism requirements because [advanceUntilIdle] always runs
   * tasks with a parallelism of 1 (i.e. serially).
   */
  @ExperimentalCoroutinesApi
  final override fun limitedParallelism(parallelism: Int): CoroutineDispatcher {
    if (delegateInterceptor !is CoroutineDispatcher) {
      throw UnsupportedOperationException(
        "limitedParallelism is not supported for WorkStealingDispatcher with " +
          "non-dispatcher delegate"
      )
    }

    val limitedDelegate = delegateInterceptor.limitedParallelism(parallelism)
    return createMatchingDelayability(
      delegateInterceptor = limitedDelegate,
      lock = lock,
      queue = queue
    )
  }

  /**
   * "Steals" work that was scheduled on this dispatcher but hasn't had a chance to run yet and runs
   * it, until there is no work left to do. If the work schedules more work, that will also be ran
   * before the method returns.
   *
   * This method is safe to call reentrantly (a continuation resumed by it can call it again).
   *
   * It is also safe to call from multiple threads, even in parallel, although the behavior is
   * undefined. E.g. One thread might return from this method before the other has finished running
   * all tasks.
   */
  // If we need a strong guarantee for calling from multiple threads we could just run this method
  // with a separate lock so all threads would just wait on the first one to finish running, but
  // that could deadlock if any of the dispatched coroutines call this method reentrantly.
  fun advanceUntilIdle() {
    do {
      val task = nextTask()
      task?.releaseAndRun()
    } while (task != null)
  }

  /**
   * Removes and returns the next task to run from the queue.
   */
  private fun nextTask(): DelegateDispatchedContinuation? {
    lock.withLock {
      val iterator = queue.iterator()
      if (iterator.hasNext()) {
        val task = iterator.next()
        iterator.remove()
        return task
      } else {
        return null
      }
    }
  }

  protected inner class DelegateDispatchedContinuation(
    override val context: CoroutineContext,
    private val runnable: Runnable
  ) : Continuation<Unit> {

    /**
     * Flag used to avoid checking the queue for the task when this continuation is executed by the
     * delegate dispatcher after it's already been ran by advancing. This is best-effort – if
     * there's a race, the losing thread will still lock and check the queue before nooping.
     *
     * Access to this property does not need to be synchronized with [lock] or by any other method,
     * since it's just a write-once hint.
     */
    @Volatile
    private var consumed = false

    /**
     * Cache for intercepted coroutine so we can release it from [resumeWith].
     * [WorkStealingDispatcher] guarantees only one resume call will happen until the continuation
     * is done, so we don't need to guard this property with a lock.
     */
    private var intercepted: Continuation<Unit>? = null

    /**
     * Resumes this continuation on [delegateInterceptor] by intercepting it and resuming the
     * intercepted continuation.
     *
     * When a dispatcher returns false from [isDispatchNeeded], then when continuations intercepted
     * by it are resumed, they may either be ran in-place or scheduled to the coroutine runtime's
     * internal, thread-local event loop (see the kdoc for [Dispatchers.Unconfined] for more
     * information on the event loop). The only way to access this internal scheduling behavior is
     * to have the dispatcher intercept a continuation and resume the intercepted continuation.
     */
    fun resumeOnDelegateDispatcher() {
      val intercepted = delegateInterceptor.interceptContinuation(this).also {
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
      // Fastest path: If this continuation has already been ran by advancing, don't even bother
      // locking and checking the queue. Note that even if consumed is false, the task may have been
      // ran already, so we still need to check whether it's in the queue under lock.
      if (consumed) return

      // Fast path: If we're racing with another thread and consumed hasn't been set yet, then check
      // the queue under lock. The queue is the real source of truth.
      val unconsumedForSure = lock.withLock {
        queue.remove(this)
      }
      if (unconsumedForSure) {
        releaseAndRun()
      }
    }

    /**
     * Runs the continuation, notifying the interceptor to release it if necessary.
     *
     * This method *MUST* only be called if and after the continuation has been successfully removed
     * from [queue], otherwise another thread may end up running it as well.
     */
    fun releaseAndRun() {
      // This flag must be set here, since this is the method that is called by advanceUntilIdle.
      consumed = true

      intercepted?.let {
        if (it !== this) {
          delegateInterceptor.releaseInterceptedContinuation(it)
        }
        intercepted = null
      }
      runnable.run()
    }
  }
}

@OptIn(InternalCoroutinesApi::class)
private class DelayableWorkStealingDispatcher(
  delegate: ContinuationInterceptor,
  delay: Delay,
  lock: Lock?,
  queue: LinkedHashSet<DelegateDispatchedContinuation>?
) : WorkStealingDispatcher(delegate, lock, queue), Delay by delay
