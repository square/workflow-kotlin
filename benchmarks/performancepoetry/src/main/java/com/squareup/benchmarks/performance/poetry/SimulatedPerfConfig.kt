package com.squareup.benchmarks.performance.poetry

/**
 * We use this to 'simulate' different performance scenarios that we have seen that we want to
 * be able to benchmark and monitor. Firstly we have a complexity which is just used to add some
 * delay to selection activities - only use with Poetry right now.
 *
 * [useInitializingState] is a smell we have observed whereby an 'initializing' state is used
 * while waiting for the first values before doing the real Workflow work.
 */
data class SimulatedPerfConfig(
  val isComplex: Boolean,
  val complexityDelay: Long,
  val useInitializingState: Boolean
) {
  companion object {
    val NO_SIMULATED_PERF = SimulatedPerfConfig(
      isComplex = false,
      useInitializingState = false,
      complexityDelay = 0
    )
  }
}
