package com.squareup.workflow1.ui.internal.test

import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * Adapted from Dispatch
 * https://github.com/RBusarow/Dispatch/blob/main/dispatch-android-espresso/src/main/java/dispatch/android/espresso/IdlingDispatcher.kt
 *
 * [IdlingResource] helper for coroutines.  This class simply wraps a delegate [CoroutineDispatcher]
 * and keeps a running count of all coroutines it creates, decrementing the count when they complete.
 *
 * If a coroutine dispatches to another dispatcher, this dispatcher will remain non-idle until the
 * outer coroutine has completed, or until the coroutines reach an *inactive* `suspend` point.  In
 * other words, if the tracked coroutine is suspended because it's waiting on a blocking I/O
 * operation, then the dispatcher will not be idle.  If the tracked coroutine is suspended because
 * it's waiting on something like a timer or user input, then the dispatcher will transition to
 * idle.
 */
public class IdlingDispatcher(
  private val delegate: CoroutineDispatcher
) : CoroutineDispatcher() {

  /**
   * The [CountingIdlingResource] which is responsible for [Espresso] functionality.
   */
  public val counter: CountingIdlingResource = CountingIdlingResource("IdlingResource for $this")

  /**
   * @return
   * * true if the [counter]'s count is zero
   * * false if the [counter]'s count is non-zero
   */
  public fun isIdle(): Boolean = counter.isIdleNow

  override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

  /**
   * Counting implementation of the [dispatch][CoroutineDispatcher.dispatch] function.
   *
   * The count is incremented for every dispatch, and decremented for every completion, including suspension.
   */
  override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {

    val runnable = Runnable {
      counter.increment()
      try {
        block.run()
      } finally {
        counter.decrement()
      }
    }
    delegate.dispatch(context + delegate, runnable)
  }

  override fun toString(): String = "IdlingDispatcher delegating to $delegate"
}
