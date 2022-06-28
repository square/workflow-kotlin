package com.squareup.workflow1

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Could use [PausableMonotonicFrameClock] but we'd need to wrap that around something.
 */
internal class WorkflowRuntimeClock(
  private var workflowFrameLatch: Latch
) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    workflowFrameLatch.await()
    return onFrame(0L) // frame time not used in Compose runtime.
  }
}

/**
 * Class internal to androidx.compose.runtime. Useful here!
 */
internal class Latch {

  private val lock = Any()
  private var awaiters = mutableListOf<Continuation<Unit>>()
  private var spareList = mutableListOf<Continuation<Unit>>()

  private var _isOpen = true
  val isOpen get() = synchronized(lock) { _isOpen }

  inline fun <R> withClosed(block: () -> R): R {
    closeLatch()
    return try {
      block()
    } finally {
      openLatch()
    }
  }

  fun closeLatch() {
    synchronized(lock) {
      _isOpen = false
    }
  }

  fun openLatch() {
    synchronized(lock) {
      if (isOpen) return

      // Rotate the lists so that if a resumed continuation on an immediate dispatcher
      // bound to the thread calling openLatch immediately awaits again we don't disrupt
      // iteration of resuming the rest. This is also why we set isClosed before resuming.
      val toResume = awaiters
      awaiters = spareList
      spareList = toResume
      _isOpen = true

      for (i in 0 until toResume.size) {
        toResume[i].resume(Unit)
      }
      toResume.clear()
    }
  }

  suspend fun await() {
    if (isOpen) return

    suspendCancellableCoroutine<Unit> { co ->
      synchronized(lock) {
        awaiters.add(co)
      }

      co.invokeOnCancellation {
        synchronized(lock) {
          awaiters.remove(co)
        }
      }
    }
  }
}
