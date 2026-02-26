package com.squareup.sample.thingy

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

// TODO this is rough sketch, there are races
internal class BackStackDispatcher : CoroutineDispatcher() {

  private val lock = Any()
  private val tasks = mutableListOf<Runnable>()
  private var capturingTasks = false
  private var delegate: CoroutineDispatcher? = null
  private var onIdle: (() -> Unit)? = null

  /**
   * Runs [block] then immediately runs all dispatched tasks before returning.
   */
  fun runThenDispatchImmediately(block: () -> Unit) {
    synchronized(lock) {
      check(!capturingTasks) { "Cannot capture again" }
      capturingTasks = true
    }
    try {
      block()
    } finally {
      // Drain tasks before clearing capturing tasks so any tasks that dispatch are also captured.
      drainTasks()
      synchronized(lock) {
        capturingTasks = false
      }
      // Run one last time in case tasks were enqueued while clearing the capture flag.
      drainTasks()
    }
  }

  /**
   * Suspends this coroutine indefinitely and dispatches any tasks to the current dispatcher.
   * [onIdle] is called after processing tasks when there are no more tasks to process.
   */
  @OptIn(ExperimentalStdlibApi::class)
  suspend fun runDispatch(onIdle: () -> Unit): Nothing {
    val delegate = currentCoroutineContext()[CoroutineDispatcher] ?: Dispatchers.Default
    synchronized(lock) {
      check(this.delegate == null) { "Expected runDispatch to only be called once concurrently" }
      this.delegate = delegate
      this.onIdle = onIdle
    }

    try {
      awaitCancellation()
    } finally {
      synchronized(lock) {
        this.delegate = null
        this.onIdle = null
      }
    }
  }

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {
    var isCapturing: Boolean
    var isFirstTask: Boolean
    var delegate: CoroutineDispatcher?
    var onIdle: (() -> Unit)?

    synchronized(lock) {
      tasks += block
      isFirstTask = tasks.size == 1
      isCapturing = this.capturingTasks
      delegate = this.delegate
      onIdle = this.onIdle
    }

    if (!isCapturing && delegate != null && onIdle != null && isFirstTask) {
      delegate!!.dispatch(context) {
        // Only run onIdle if work was actually done.
        if (drainTasks()) {
          onIdle!!()
        }
      }
    }
  }

  /**
   * Returns true if any tasks were executed.
   */
  private fun drainTasks(): Boolean {
    var didAnything = false
    var task = getNextTask()
    while (task != null) {
      didAnything = true
      task.run()
      task = getNextTask()
    }
    return didAnything
  }

  private fun getNextTask(): Runnable? {
    synchronized(lock) {
      return tasks.removeFirstOrNull()
    }
  }
}
