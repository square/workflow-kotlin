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
   * This is the baseline runtime which will process one action at a time, calling render() after
   * each one.
   */
  public object RenderPerAction : RuntimeConfig

  /**
   * If we have more actions to process, do so before passing the rendering to the UI layer.
   */
  @WorkflowExperimentalRuntime
  public object ConflateStaleRenderings : RuntimeConfig

  public companion object {
    public val DEFAULT_CONFIG: RuntimeConfig = RenderPerAction
  }
}
