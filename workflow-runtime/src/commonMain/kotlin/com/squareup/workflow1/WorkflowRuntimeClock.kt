package com.squareup.workflow1

import androidx.compose.runtime.MonotonicFrameClock
import com.squareup.workflow1.internal.nanoTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.single

public class WorkflowRuntimeClock(
  private val workflowFrames: Flow<Unit>
) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    workflowFrames.single()
    return onFrame(nanoTime())
  }
}
