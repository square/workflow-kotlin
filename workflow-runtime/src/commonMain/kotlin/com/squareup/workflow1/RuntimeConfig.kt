package com.squareup.workflow1

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY

/**
 * This is used to mark any experimental runtimes.
 */
@Target(
  AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION,
  AnnotationTarget.TYPEALIAS
)
@MustBeDocumented
@Retention(value = BINARY)
@RequiresOptIn(level = ERROR)
public annotation class WorkflowExperimentalRuntime

/**
 * A specification of the Workflow Runtime.
 */
public sealed interface RuntimeConfig {
  /**
   * This version of the runtime will process as many actions as possible after one is received
   * until [frameTimeoutMs] has passed, at which point it will render().
   */
  @WorkflowExperimentalRuntime
  public data class FrameTimeout(public val frameTimeoutMs: Long = 30L) : RuntimeConfig

  /**
   * This is the baseline runtime which will process one action at a time, calling render() after
   * each one.
   */
  public object RenderPerAction : RuntimeConfig

  public companion object {
    public val DEFAULT_CONFIG: RuntimeConfig = RenderPerAction
  }
}
