package com.squareup.benchmarks.performance.complex.poetry.instrumentation

/**
 * Stats for one Render Pass.
 */
data class RenderStats(
  var nodesRenderedFresh: Int = 0,
  var nodesRenderedStale: Int = 0
) {
  operator fun plusAssign(renderStats: RenderStats) {
    nodesRenderedFresh += renderStats.nodesRenderedFresh
    nodesRenderedStale += renderStats.nodesRenderedStale
  }

  fun reset() {
    nodesRenderedFresh = 0
    nodesRenderedStale = 0
  }
}

/**
 * Stats for a Workflow tree's entire lifetime.
 */
data class RenderEfficiency(
  var totalRenderPasses: Int = 0,
  val totalNodeStats: RenderStats = RenderStats()
) {
  val freshRenderingRatio: Double
    get() = totalNodeStats.nodesRenderedFresh.toDouble() /
      (totalNodeStats.nodesRenderedStale + totalNodeStats.nodesRenderedFresh).toDouble()

  fun reset() {
    totalRenderPasses = 0
    totalNodeStats.reset()
  }
}
