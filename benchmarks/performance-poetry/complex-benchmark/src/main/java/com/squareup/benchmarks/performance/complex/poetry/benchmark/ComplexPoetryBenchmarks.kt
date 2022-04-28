package com.squareup.benchmarks.performance.complex.poetry.benchmark

import android.content.Context
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor.Companion.NODES_TO_TRACE
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
 *
 * [benchmarkTraceSections] will measure the trace timings instrumented via the
 * [PerformanceTracingInterceptor] installed by default in the Workflow tree.
 *
 * [benchmarkFrameTiming] measures frame timing but it is still WIP and not useful in its current
 * form as there is not enough scrolling/animation in the scenario.
 *
 * The above can be run as tests using Full, Partial, or No aot compiling on the app.
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
   * This is a LONG test. Searching and pulling form the perfetto trace after each
   * iteration takes a long time. This test with 20 iterations runs for 1 hr 12 m on
   * a Nexus 6.
   */
  @Test fun benchmarkTraceSectionsFullAOT() {
    benchmarkTraceSections(CompilationMode.Full())
  }

  @OptIn(ExperimentalMetricApi::class)
  private fun benchmarkTraceSections(compilationMode: CompilationMode) {
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
      }
    ) {
      device.landscapeOrientation()
      // Use default performance config for now, so no need to customize intent.
      startActivityAndWait()

      device.openRavenAndNavigate()
    }
  }

  @Test fun benchmarkFrameTimingNoCompilation() {
    benchmarkFrameTiming(CompilationMode.None())
  }

  @Test fun benchmarkFrameTimingPartialAOTWithProfile() {
    benchmarkFrameTiming(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))
  }

  @Test fun benchmarkFrameTimingFullAOT() {
    benchmarkFrameTiming(CompilationMode.Full())
  }

  @OptIn(ExperimentalMetricApi::class)
  private fun benchmarkFrameTiming(compilationMode: CompilationMode) {
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(FrameTimingMetric()),
      iterations = 1,
      startupMode = StartupMode.WARM,
      compilationMode = compilationMode,
      setupBlock = {
        pressHome()
      }
    ) {
      startActivityAndWait()

      // N.B. This is *not* a good scenario to measure frame timing as there isn't much scrolling
      // or animation involved. This benchmark needs another app scenario.
      device.openRavenAndNavigate()
    }
  }

  companion object {
    const val RENDER_PASSES = 61
    const val PACKAGE_NAME = "com.squareup.benchmarks.performance.complex.poetry"
  }
}
