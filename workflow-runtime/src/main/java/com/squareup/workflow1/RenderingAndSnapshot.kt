package com.squareup.workflow1

/**
 * Tuple of rendering and snapshot used by [renderWorkflowIn].
 *
 * Note that this class keeps the default identity equality
 * implementation it inherits from `Any`, rather than comparing
 * its [rendering] or [snapshot].
 */
public class RenderingAndSnapshot<out RenderingT>(
  public val rendering: RenderingT,
  public val snapshot: TreeSnapshot
) {
  public operator fun component1(): RenderingT = rendering
  public operator fun component2(): TreeSnapshot = snapshot
}
