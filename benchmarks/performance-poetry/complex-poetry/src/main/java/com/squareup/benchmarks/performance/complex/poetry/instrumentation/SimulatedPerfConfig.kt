package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * We use this to 'simulate' different performance scenarios that we have seen that we want to
 * be able to benchmark and monitor. Firstly we have a complexity which is just used to add some
 * delay to selection activities - only use with Poetry right now.
 *
 * [useInitializingState] is a smell we have observed whereby an 'initializing' state is used
 * while waiting for the first values before doing the real Workflow work.
 */
@Parcelize
data class SimulatedPerfConfig(
  val isComplex: Boolean,
  val complexityDelay: Long,
  val useInitializingState: Boolean,
  val traceRenderingPasses: Boolean = false,
  val traceLatency: Boolean = false
) : Parcelable {
  companion object {
    val NO_SIMULATED_PERF = SimulatedPerfConfig(
      isComplex = false,
      complexityDelay = 0,
      useInitializingState = false,
      traceRenderingPasses = false,
      traceLatency = false
    )
  }
}
