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
 * For the rest of the benchmarks, see individual kdoc.
 */
@OptIn(ExperimentalMetricApi::class)
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
    benchmarkNodeAndRenderPassTraceSections()
  }

  /**
   * If you thought the test above was long, this one is 10x as long because of how
   * many render pass sections we are tracing (though we try to sample only half of them).
   */
  @Test fun benchmarkNodeAndRenderPassTraceSectionsFullAotHighFrequency() {
    benchmarkNodeAndRenderPassTraceSections(iterations = 5, useHighFrequencyEvents = true)
  }

  private fun benchmarkNodeAndRenderPassTraceSections(
    iterations: Int = 10,
    useHighFrequencyEvents: Boolean = false
  ) {
    val renderPasses =
      if (useHighFrequencyEvents) RENDER_PASSES_HIGH_FREQUENCY / 2 else RENDER_PASSES
    val traceMetricsList = List(renderPasses + 1) { i ->
      // Just sample every other render pass for high frequency.
      val index = if (useHighFrequencyEvents) i * 2 else i
      val passNumber = index.toString()
        .padStart(PerformanceTracingInterceptor.RENDER_PASS_DIGITS, '0')
      val sectionName = "${passNumber}_Render_Pass_"
      TraceSectionMetric(sectionName)
    }.toMutableList()
    if (!useHighFrequencyEvents) {
      // If we don't have high frequency events then trace each node.
      traceMetricsList += NODES_TO_TRACE.flatMap { node ->
        List(renderPasses + 1) { i ->
          val passNumber = i.toString()
            .padStart(PerformanceTracingInterceptor.RENDER_PASS_DIGITS, '0')
          val sectionName = "${passNumber}_Render_Pass_Node_${node.first}_"
          TraceSectionMetric(sectionName)
        }
      }
    }

    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = traceMetricsList,
      iterations = iterations,
      startupMode = StartupMode.WARM,
      compilationMode = CompilationMode.Full(),
      setupBlock = {
        pressHome()
        device.landscapeOrientation()
      }
    ) {
      startActivityAndWait { intent ->
        intent.apply {
          putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_INITIALIZING, true)
          putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_RENDERING, true)
          if (useHighFrequencyEvents) {
            putExtra(
              PerformancePoetryActivity.EXTRA_PERF_CONFIG_REPEAT,
              PerformancePoetryActivity.HIGH_FREQUENCY_REPEAT_COUNT
            )
          }
        }
      }
      device.landscapeOrientation()

      device.openRavenAndNavigate()
    }
  }

  @Test fun benchmarkActionTraceSectionsRegularFrequency() = benchmarkActionTraceSections()

  @Test fun benchmarkActionTraceSectionsHighFrequency() = benchmarkActionTraceSections(
    highFrequency = true,
    iterations = 5
  )

  /**
   * This test is focused on measuring the length of Workflow's 'work'. By that we mean how much
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
   *
   * This only works to measure 'latency' if we handle each event -> render() cycle in one main
   * thread message as we are taking advantage of that fact in setting up the trace sections.
   * If this is not the case and we care most about latency itself, then use
   * [benchmarkLatencyWithFrameCallbacks()] as an alternate heuristic.
   *
   * However, if we just want to measure the total amount of time Workflow 'works' for the
   * scenario under test, then we can add up all these action handling sections as long
   * as they are all being recorded in the sections map - [ACTION_TRACE_SECTIONS]. Note that
   * this is an iterative process for the scenario and after any change to how the Workflow
   * runtime processes actions as we will need to record all the different action processing
   * labels and ensure they are included in the map. To validate we cover all of Workflow's work
   * it is a good idea to add in tracing for all main thread messages and take a look at the
   * Perfetto trace to see if there is any 'work' we are missing. We can do that by adding
   * [PerformancePoetryActivity.EXTRA_TRACE_ALL_MAIN_THREAD_MESSAGES].
   */
  private fun benchmarkActionTraceSections(
    highFrequency: Boolean = false,
    iterations: Int = 10
  ) {
    fun addActionTracing(intent: Intent) {
      intent.apply {
        if (highFrequency) {
          putExtra(
            PerformancePoetryActivity.EXTRA_PERF_CONFIG_REPEAT,
            PerformancePoetryActivity.HIGH_FREQUENCY_REPEAT_COUNT
          )
        }
        putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_INITIALIZING, true)
        putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_ACTION_TRACING, true)
      }
    }

    val traceSections =
      if (highFrequency) ACTION_TRACE_SECTIONS_HIGH_FREQUENCY else ACTION_TRACE_SECTIONS

    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = traceSections.map { TraceSectionMetric(it) },
      iterations = iterations,
      startupMode = StartupMode.WARM,
      compilationMode = CompilationMode.Full(),
      setupBlock = {
        pressHome()
        device.landscapeOrientation()
      }
    ) {
      startActivityAndWait(::addActionTracing)
      device.landscapeOrientation()
      device.waitForIdle()

      device.openRavenAndNavigate()
    }
  }

  /**
   * This measures latency using the Papa method where for each rendering we trace from the start
   * of the interaction event to the time after Choreographer has painted the changes using a
   * frame callback. Note that these *include* the time to paint the first frame by Choreographer.
   *
   * These are still helpful though if we process in the Workflow runtime with multiple main thread
   * messages (e.g. processing multiple action selects).
   *
   * This also does not include the time that the Workflow runtime uses to handle the actual UI
   * interaction event and produce a new rendering. In practice for this application the render
   * pass times are < 1ms as we don't have a lot of render() pathologies and work. There isn't
   * anything we need to track there either as optimization for this is covered by reducing the
   * # of total render passes, and specifically for stale nodes.
   *
   * What this gives us is a high level look at whether processing multiple actions or other such
   * optimizations will increase the overall latency.
   */
  @Test fun benchmarkLatencyWithFrameCallbacks() {
    fun addLatencyTracing(intent: Intent) {
      intent.apply {
        putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_INITIALIZING, true)
        putExtra(PerformancePoetryActivity.EXTRA_PERF_CONFIG_FRAME_LATENCY, true)
      }
    }

    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = FRAME_LATENCY_TRACE_SECTIONS.map { TraceSectionMetric(it) },
      iterations = 10,
      startupMode = StartupMode.WARM,
      compilationMode = CompilationMode.Full(),
      setupBlock = {
        pressHome()
        device.landscapeOrientation()
      }
    ) {
      startActivityAndWait(::addLatencyTracing)
      device.landscapeOrientation()
      device.waitForIdle()

      device.openRavenAndNavigate()
    }
  }

  companion object {
    const val RENDER_PASSES = 57
    const val RENDER_PASSES_HIGH_FREQUENCY = 181
    const val PACKAGE_NAME = "com.squareup.benchmarks.performance.complex.poetry"

    val ACTION_TRACE_SECTIONS = listOf(
      "E-PoemList-PoemSelected-00",
      "Worker-ComplexCallBrowser(2)-Finished-00",
      "E-StanzaList-StanzaSelected-00",
      "E-StanzaList-Exit-00",
      "Worker-ComplexCallBrowser(-1)-Finished-00",
    ) + (0..11).map {
      "Worker-PoemLoading-Finished-${it.pad()}"
    } + (0..4).map {
      "E-StanzaWorkflow-ShowNextStanza-${it.pad()}"
    } + (0..4).map {
      "E-StanzaWorkflow-ShowPreviousStanza-${it.pad()}"
    }

    val ACTION_TRACE_SECTIONS_HIGH_FREQUENCY = ACTION_TRACE_SECTIONS + (0..250).map {
      "Worker-EventRepetition-Finished-${it.pad()}"
    }

    val FRAME_LATENCY_TRACE_SECTIONS = (0..27).map {
      "Frame-Latency-${it.pad()}_"
    }

    private fun Int.pad() =
      toString().padStart(3, '0')
  }
}
