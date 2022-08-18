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
   * This version of the runtime will process all actions queued within [actionWaitMs]
   * (i.e. cascade up the tree only) before calling render() once. If [frameTimeoutMs] has passed
   * while processing actions, it will stop and call render(). After render() is called it will
   * pass the new rendering to the UI (in other words it can't process forever if actions are
   * queueing faster than [actionWaitMs]).
   *
   * i.e. this is one "Render Pass" per 'frame'.
   */
  @WorkflowExperimentalRuntime
  public data class RenderPassPerFrame(
    public val actionWaitMs: Long = 10L,
    public val frameTimeoutMs: Long = 300L
  ) : RuntimeConfig

  /**
   * This version of the runtime will process all actions queued within [actionWaitMs] AND call
   * render() after processing them (i.e. cascade up and down the tree) before passing the
   * updated rendering to the UI or until [frameTimeoutMs] has passed, at which point it will
   * pass the new rendering to the UI (in other words it can't process forever if actions are
   * queueing faster than [actionWaitMs]).
   *
   * i.e. this is one "Rendering" per 'frame'.
   */
  @WorkflowExperimentalRuntime
  public data class RenderingPerFrame(
    public val actionWaitMs: Long = 10L,
    public val frameTimeoutMs: Long = 300L
  ) : RuntimeConfig

  /**
   * This is the baseline runtime which will process one action at a time, calling render() after
   * each one.
   *
   * i.e. this is one "Render Pass" and one "Rendering" per 'action'.
   */
  public object RenderPassPerAction : RuntimeConfig

  public companion object {
    public val DEFAULT_CONFIG: RuntimeConfig = RenderPassPerAction
  }
}
