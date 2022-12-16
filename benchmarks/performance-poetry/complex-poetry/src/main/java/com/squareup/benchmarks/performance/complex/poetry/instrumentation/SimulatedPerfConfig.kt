package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import android.os.Parcelable
import com.squareup.sample.poetry.RecursionGraphConfig
import kotlinx.parcelize.Parcelize

/**
 * We use this to 'simulate' different performance scenarios that we have seen that we want to
 * be able to benchmark and monitor. Firstly we have a complexity which is just used to add some
 * delay to selection activities - only use with Poetry right now.
 *
 * [isComplex] Determines whether or not we start a Worker in between state transitions that
 *   roughly approximates doing I/O work or a network call.
 * [complexityDelay] Is the length of the delay for the work in between the state changes if
 *   [isComplex] is true.
 * [useInitializingState] is a smell we have observed whereby an 'initializing' state is used
 *   while waiting for the first values before doing the real Workflow work.
 * [recursionGraph] Determines the shape of the tree that is rendered by the Workflow runtime. The
 *   first number n is the recursive 'depth'. This will have the [PerformancePoemBrowserWorkflow]
 *   render itself recursively n times, before rendering its actual children - the poem list and
 *   the poem. The second number m is the 'breadth'. This means that for each recursive depth layer
 *   n, the [PerformancePoemBrowserWorkflow] will also be rendered as siblings m times. Only the
 *   (m-1)th rendering is returned from render() though which means that the other renders will be
 *   work for the runtime to render but they won't be passed to the UI layer.
 * [repeatOnNext] Is the number of times x that an action should be created when 'next' is pressed
 *   in the poem before the action takes its effect - advances the state to the next stanza.
 * [simultaneousActions] Determines the number of workers y that should be created for each
 *   'complex' state transition. This will result in y actions that need to be handled
 *   simultaneously and tries to represent a scenario of multiple Workflows listening to the same
 *   action.
 * [traceRenderingPasses], [traceFrameLatency], [traceEventLatency] are all flags to add
 *   instrumentation for different performance measurements.
 */
@Parcelize
data class SimulatedPerfConfig(
  val isComplex: Boolean,
  val complexityDelay: Long,
  val useInitializingState: Boolean,
  val recursionGraph: RecursionGraphConfig = 0 to 0,
  val repeatOnNext: Int = 0,
  val simultaneousActions: Int = 0,
  val traceRenderingPasses: Boolean = false,
  val traceFrameLatency: Boolean = false,
  val traceEventLatency: Boolean = false
) : Parcelable {
  companion object {
    val NO_SIMULATED_PERF = SimulatedPerfConfig(
      isComplex = false,
      complexityDelay = 0,
      useInitializingState = false,
      recursionGraph = 0 to 0,
      repeatOnNext = 0,
      simultaneousActions = 0,
      traceRenderingPasses = false,
      traceFrameLatency = false,
      traceEventLatency = false
    )
  }
}
