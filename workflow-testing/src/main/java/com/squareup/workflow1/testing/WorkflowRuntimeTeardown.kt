package com.squareup.workflow1.testing

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Controls how `renderForTest` tears down the workflow runtime after the test block completes.
 */
public sealed interface WorkflowRuntimeTeardown {
  /**
   * Cancel the workflow runtime and return without waiting for cancellation cleanup to complete.
   */
  public data object Cancel : WorkflowRuntimeTeardown

  /**
   * Cancel the workflow runtime and wait for cancellation cleanup to complete.
   *
   * @param timeout The maximum time to wait for the runtime job to complete after cancellation.
   * @param drainSchedulerAfterCancel Whether to drain the test scheduler after cancelling the
   * runtime, and again after the runtime job completes.
   */
  public data class CancelAndAwait(
    public val timeout: Duration = 1.seconds,
    public val drainSchedulerAfterCancel: Boolean = true,
  ) : WorkflowRuntimeTeardown
}
