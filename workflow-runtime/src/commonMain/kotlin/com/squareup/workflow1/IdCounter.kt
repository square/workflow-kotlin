package com.squareup.workflow1

/**
 * Monotonically-increasing counter that produces longs, used to assign
 * [com.squareup.workflow1.WorkflowInterceptor.WorkflowSession.sessionId].
 */
public class IdCounter {
  private var nextId = 0L
  public fun createId(): Long = nextId++
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun IdCounter?.createId(): Long = this?.createId() ?: 0
