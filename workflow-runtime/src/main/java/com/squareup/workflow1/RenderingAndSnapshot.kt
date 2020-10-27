package com.squareup.workflow1

/**
 * Tuple of rendering and snapshot used by [renderWorkflowIn].
 *
 * Note that this class keeps the default identity equality
 * implementation it inherits from `Any`, rather than comparing
 * its [rendering] or [snapshot].
 */
class RenderingAndSnapshot<out RenderingT>(
  val rendering: RenderingT,
  val snapshot: TreeSnapshot
) {
  operator fun component1() = rendering
  operator fun component2() = snapshot
}
