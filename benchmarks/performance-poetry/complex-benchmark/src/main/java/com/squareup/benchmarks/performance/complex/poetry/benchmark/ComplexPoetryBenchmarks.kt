package com.squareup.benchmarks.performance.complex.poetry.benchmark

import android.content.Context
import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor.Companion.NODES_TO_TRACE
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.SimulatedPerfConfig
import com.squareup.benchmarks.performance.complex.poetry.robots.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.robots.openRavenAndNavigate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A set of macro-benchmarks for the Complex Poetry Application. See benchmarks/README for more
 * info.
 *
 * [benchmarkStartup] will measure startup times with different compilation modes.
 * The above can be run as tests using Full, Partial, or No aot compiling on the app.
 *
 * [benchmarkNodeAndRenderPassTraceSections] will measure the trace timings instrumented via the
 * [PerformanceTracingInterceptor] installed by default in the Workflow tree.
 *
 * [benchmarkLatencyTraceSections] will measure the time between a UI event to producing a
 * Rendering and the time between the Rendering and the Choreographer rendering the frame.
 */
@RunWith(AndroidJUnit4::class)
class ComplexPoetryBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  private lateinit var context: Context
  private lateinit var device: UiDevice

  @Before fun setUp() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    context = ApplicationProvider.getApplicationContext()
    device = UiDevice.getInstance(instrumentation)
  }

  @Test fun benchmarkStartupNoCompilation() {
    benchmarkStartup(CompilationMode.None())
  }

  @Test fun benchmarkStartupPartialAOTWithProfile() {
    benchmarkStartup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))
  }

  @Test fun benchmarkStartupFullAOT() {
    benchmarkStartup(CompilationMode.Full())
  }

  private fun benchmarkStartup(compilationMode: CompilationMode) {
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric()),
      iterations = 20,
      startupMode = StartupMode.COLD,
      compilationMode = compilationMode,
      setupBlock = {
        pressHome()
      }
    ) {
      startActivityAndWait()
    }
  }

  /**
   * This is a LONG test. Searching and pulling form the Perfetto trace after each
   * iteration takes a long time. This test with 20 iterations runs for 1 hr 12 m on
   * a Nexus 6.
   */
  @Test fun benchmarkNodeAndRenderPassTraceSectionsFullAot() {
    benchmarkNodeAndRenderPassTraceSections(CompilationMode.Full())
  }

  @OptIn(ExperimentalMetricApi::class)
  private fun benchmarkNodeAndRenderPassTraceSections(compilationMode: CompilationMode) {
    val traceMetricsList = NODES_TO_TRACE.flatMap { node ->
      List(RENDER_PASSES + 1) { i ->
        val passNumber = i.toString()
          .padStart(PerformanceTracingInterceptor.RENDER_PASS_DIGITS, '0')
        val sectionName = "${passNumber}_Render_Pass_Node_${node.first}_"
        TraceSectionMetric(sectionName)
      }
    } + List(RENDER_PASSES + 1) { i ->
      val passNumber = i.toString()
        .padStart(PerformanceTracingInterceptor.RENDER_PASS_DIGITS, '0')
      val sectionName = "${passNumber}_Render_Pass_"
      TraceSectionMetric(sectionName)
    }

    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = traceMetricsList,
      iterations = 20,
      startupMode = StartupMode.WARM,
      compilationMode = compilationMode,
      setupBlock = {
        pressHome()
        device.landscapeOrientation()
      }
    ) {
      startActivityAndWait{
        val renderPassConfig = SimulatedPerfConfig(
          isComplex = true,
          complexityDelay = 200L,
          useInitializingState = true,
          traceRenderingPasses = true,
          traceLatency = false
        )
        it.putExtra(PerformancePoetryActivity.PERF_CONFIG_EXTRA, renderPassConfig)
      }
      device.landscapeOrientation()

      device.openRavenAndNavigate()
    }
  }

  /**
   * Another LONG test.
   */
  @Test fun benchmarkLatencyTraceSectionsFullAot() {
    benchmarkLatencyTraceSections(CompilationMode.Full())
  }

  /**
   * This test is focused on two different measurements:
   *
   * Frame-Latency-N: is the trace between passing the Rendering to the view layer and the
   *  'post frame rendered callback' for the Nth frame in the scenario. In other words, this traces
   *  the time it takes from a Rendering produced by Workflow to process through the Workflow UI
   *  layer and then be rendered in the next frame.
   *
   * XScreen-onY-Z: is the time between the execution of event handler 'onY' and the production of
   *   the next root Rendering by Workflow for the Zth instance of the 'onY' handler on X Screen.
   *   In other words, this measures the time Workflow takes in processing a UI event into a new
   *   Rendering. This will be similar to the render pass traced above, but more comprehensive to
   *   include all of the event handling time.
   */
  @OptIn(ExperimentalMetricApi::class)
  fun benchmarkLatencyTraceSections(compilationMode: CompilationMode) {
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = LATENCY_TRACE_SECTIONS.map { TraceSectionMetric(it) },
      iterations = 20,
      startupMode = StartupMode.WARM,
      compilationMode = compilationMode,
      setupBlock = {
        pressHome()
        device.landscapeOrientation()
      }
    ) {
      startActivityAndWait{
        val renderPassConfig = SimulatedPerfConfig(
          isComplex = true,
          complexityDelay = 200L,
          useInitializingState = true,
          traceRenderingPasses = false,
          traceLatency = true
        )
        it.putExtra(PerformancePoetryActivity.PERF_CONFIG_EXTRA, renderPassConfig)
      }
      device.landscapeOrientation()

      device.openRavenAndNavigate()
    }
  }

  companion object {
    const val RENDER_PASSES = 61
    const val PACKAGE_NAME = "com.squareup.benchmarks.performance.complex.poetry"

    val LATENCY_TRACE_SECTIONS = listOf(
      "PoemListScreen-onPoemSelected(2)-1 ",
      "StanzaListScreen-onStanzaSelected(4)-1 ",
      "StanzaScreen-onGoForth-1 ",
      "StanzaScreen-onGoForth-2 ",
      "StanzaScreen-onGoForth-3 ",
      "StanzaScreen-onGoForth-4 ",
      "StanzaScreen-onGoForth-5 ",
      "StanzaScreen-onGoBack-1 ",
      "StanzaScreen-onGoBack-2 ",
      "StanzaScreen-onGoBack-3 ",
      "StanzaScreen-onGoBack-4 ",
      "StanzaScreen-onGoBack-5 ",
      "StanzaListScreen-onExit-1 ",
      "Frame-Latency-00 ",
      "Frame-Latency-01 ",
      "Frame-Latency-02 ",
      "Frame-Latency-03 ",
      "Frame-Latency-04 ",
      "Frame-Latency-05 ",
      "Frame-Latency-06 ",
      "Frame-Latency-07 ",
      "Frame-Latency-08 ",
      "Frame-Latency-09 ",
      "Frame-Latency-10 ",
      "Frame-Latency-11 ",
      "Frame-Latency-12 ",
      "Frame-Latency-13 ",
      "Frame-Latency-14 ",
      "Frame-Latency-15 ",
      "Frame-Latency-16 ",
      "Frame-Latency-17 ",
      "Frame-Latency-18 ",
      "Frame-Latency-19 ",
      "Frame-Latency-20 ",
      "Frame-Latency-21 ",
      "Frame-Latency-22 ",
      "Frame-Latency-23 ",
      "Frame-Latency-24 ",
      "Frame-Latency-25 ",
      "Frame-Latency-26 ",
      "Frame-Latency-27 ",
      )
  }
}
