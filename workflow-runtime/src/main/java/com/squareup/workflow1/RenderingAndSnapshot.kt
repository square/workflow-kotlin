package com.squareup.workflow1

/**
 * Tuple of rendering and snapshot used by [renderWorkflowIn].
 *
 * Note that this class keeps the default identity equality
 * implementation it inherits from `Any`, rather than comparing
 * its [rendering] or [snapshot].
 *
 * @param workInProgress A hint that another render pass is already in progress.
 * View systems may wish to ignore [rendering] until [workInProgress] is false.
 * Persistence systems, however, should never skip a [snapshot] update.
 */
public class RenderingAndSnapshot<out RenderingT>(
  public val rendering: RenderingT,
  public val snapshot: TreeSnapshot,
  public val workInProgress: Boolean
) {
  public operator fun component1(): RenderingT = rendering
  public operator fun component2(): TreeSnapshot = snapshot
  public operator fun component3(): Boolean = workInProgress
}
