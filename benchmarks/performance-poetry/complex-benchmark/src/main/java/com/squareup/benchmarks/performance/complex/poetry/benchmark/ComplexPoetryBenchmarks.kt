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
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor.Companion.NODES_TO_TRACE
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.openRavenAndNavigate
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
      startActivityAndWait { intent ->
        intent.apply {
          putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_INITIALIZING, true)
          putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_RENDERING, true)
        }
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
   * This test is focused on measuring the latency of Workflow's 'work'. By that we mean how much
   * time Workflow spends producing the next set of updates to the View's that can be passed
   * to Android to draw as a frame.
   *
   * How do we measure this? The [EventHandlingTracingInterceptor] is a [WorkflowInterceptor] that
   * sets up a [RenderContextInterceptor] which has a hook for everytime we 'send' an action to the
   * [RenderContext]. Because this is done on the main thread, we can wrap this function call in a
   * synchronous trace section as it will be equivalent to the main thread 'message' that handles
   * all of the work before invoking the Choreographer to draw the updated Views.
   *
   * We do this for certain [Worker]s results by annotating the [Worker] with a pattern in
   * its [toString()] - see [TraceableWorker].
   * We do this for UI events by adding in an injected name to the eventHandler.
   *
   * These are formatted as:
   *  - "Worker-<Work>-Finished-XX"
   *  - "E-<Screen>-<Event>-XX"
   *
   * Where XX is the counted instance of that event's response.
   */
  @OptIn(ExperimentalMetricApi::class)
  fun benchmarkLatencyTraceSections(compilationMode: CompilationMode) {
    fun addLatency(intent: Intent) {
      intent.apply {
        putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_INITIALIZING, true)
        putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_EVENT_LATENCY, true)
      }
    }

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
      startActivityAndWait(::addLatency)
      device.landscapeOrientation()
      device.waitForIdle()

      device.openRavenAndNavigate()
    }
  }

  companion object {
    const val RENDER_PASSES = 58
    const val PACKAGE_NAME = "com.squareup.benchmarks.performance.complex.poetry"

    val LATENCY_TRACE_SECTIONS = listOf(
      "E-PoemList-PoemSelected-00",
      "Worker-ComplexCallBrowser(2)-Finished-00",
      "Worker-PoemLoading-Finished-00",
      "E-StanzaList-StanzaSelected-00",
      "Worker-PoemLoading-Finished-01",
      "E-StanzaWorkflow-ShowNextStanza-00",
      "Worker-PoemLoading-Finished-02",
      "E-StanzaWorkflow-ShowNextStanza-01",
      "Worker-PoemLoading-Finished-03",
      "E-StanzaWorkflow-ShowNextStanza-02",
      "Worker-PoemLoading-Finished-04",
      "E-StanzaWorkflow-ShowNextStanza-03",
      "Worker-PoemLoading-Finished-05",
      "E-StanzaWorkflow-ShowNextStanza-04",
      "Worker-PoemLoading-Finished-06",
      "E-StanzaWorkflow-ShowPreviousStanza-00",
      "Worker-PoemLoading-Finished-07",
      "E-StanzaWorkflow-ShowPreviousStanza-01",
      "Worker-PoemLoading-Finished-08",
      "E-StanzaWorkflow-ShowPreviousStanza-02",
      "Worker-PoemLoading-Finished-09",
      "E-StanzaWorkflow-ShowPreviousStanza-03",
      "Worker-PoemLoading-Finished-10",
      "E-StanzaWorkflow-ShowPreviousStanza-04",
      "Worker-PoemLoading-Finished-11",
      "E-StanzaList-Exit-00",
      "Worker-ComplexCallBrowser(-1)-Finished-00",
      )
  }
}
