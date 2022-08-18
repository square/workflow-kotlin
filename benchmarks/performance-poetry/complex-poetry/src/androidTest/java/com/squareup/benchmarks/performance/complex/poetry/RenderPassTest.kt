package com.squareup.benchmarks.performance.complex.poetry

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_CONFIG_COUNT_RENDERINGS
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_CONFIG_HIGH_FREQ_REPEAT
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_CONFIG_SIMULTANEOUS
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_RUNTIME_RENDERING_PER_FRAME
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_RUNTIME_RENDER_PASS_PER_FRAME
import com.squareup.benchmarks.performance.complex.poetry.RenderPassTest.TestType.COUNT_RENDERINGS
import com.squareup.benchmarks.performance.complex.poetry.RenderPassTest.TestType.COUNT_RENDER_PASSES
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.openRavenAndNavigate
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.waitForPoetry
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.RenderPassCountingInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.RenderPassPerAction
import com.squareup.workflow1.RuntimeConfig.RenderPassPerFrame
import com.squareup.workflow1.RuntimeConfig.RenderingPerFrame
import com.squareup.workflow1.WorkflowExperimentalRuntime
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that is used to confirm that the number of render passes and the fresh rendering ratio
 * is constant.
 */
@OptIn(WorkflowExperimentalRuntime::class)
@RunWith(AndroidJUnit4::class)
class RenderPassTest {

  enum class TestType {
    COUNT_RENDER_PASSES,
    COUNT_RENDERINGS
  }

  data class Scenario(
    val title: String,
    val highFrequencyEventEmitting: Boolean,
    val simultaneousActions: Int,
    val renderPassPerActionExpectation: RenderExpectation,
    val renderPassPerFrameExpectation: RenderExpectation,
    val renderingPerFrameExpectation: RenderExpectation
  )

  data class RenderExpectation(
    val renderings: IntRange,
    val totalPasses: IntRange,
    val freshRenderedNodes: IntRange,
    val staleRenderedNodes: IntRange
  )

  private val renderPassCountingInterceptor = RenderPassCountingInterceptor()
  private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  private lateinit var context: Context

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()

    device.wakeUp()
    device.pressHome()
    device.waitForIdle()

    // Do these in landscape so we have both the 'index' and the 'detail' showing.
    device.landscapeOrientation()
  }

  @Test fun countRenderPass_RenderPassPerAction_BaseScenario() {
    runCountingTest(COUNT_RENDER_PASSES, BASE_SCENARIO, runtime = RenderPassPerAction)
  }

  @Test fun countRenderPass_RenderPassPerAction_HighFrequencyScenario() {
    runCountingTest(
      COUNT_RENDER_PASSES,
      HIGH_FREQUENCY_SCENARIO,
      runtime = RenderPassPerAction
    )
  }

  @Test fun countRenderPass_RenderPassPerAction_SimultaneousScenario() {
    runCountingTest(
      COUNT_RENDER_PASSES,
      SIMULTANEOUS_SCENARIO,
      runtime = RenderPassPerAction
    )
  }

  @Test fun countRenderPass_RenderPassPerFrame_BaseScenario() {
    runCountingTest(COUNT_RENDER_PASSES, BASE_SCENARIO, runtime = RenderPassPerFrame())
  }

  @Test fun countRenderPass_RenderPassPerFrame_HighFrequencyScenario() {
    runCountingTest(
      COUNT_RENDER_PASSES,
      HIGH_FREQUENCY_SCENARIO,
      runtime = RenderPassPerFrame()
    )
  }

  @Test fun countRenderPass_RenderPassPerFrame_SimultaneousScenario() {
    runCountingTest(
      COUNT_RENDER_PASSES,
      SIMULTANEOUS_SCENARIO,
      runtime = RenderPassPerFrame()
    )
  }

  @Test fun countRenderPass_RenderingPerFrame_BaseScenario() {
    runCountingTest(COUNT_RENDER_PASSES, BASE_SCENARIO, runtime = RenderingPerFrame())
  }

  @Test fun countRenderPass_RenderingPerFrame_HighFrequencyScenario() {
    runCountingTest(
      COUNT_RENDER_PASSES,
      HIGH_FREQUENCY_SCENARIO,
      runtime = RenderingPerFrame()
    )
  }

  @Test fun countRenderPass_RenderingPerFrame_SimultaneousScenario() {
    runCountingTest(
      COUNT_RENDER_PASSES,
      SIMULTANEOUS_SCENARIO,
      runtime = RenderingPerFrame()
    )
  }

  @Test fun countRendering_RenderPassPerAction_BaseScenario() {
    runCountingTest(COUNT_RENDERINGS, BASE_SCENARIO, runtime = RenderPassPerAction)
  }

  @Test fun countRendering_RenderPassPerAction_HighFrequencyScenario() {
    runCountingTest(
      COUNT_RENDERINGS,
      HIGH_FREQUENCY_SCENARIO,
      runtime = RenderPassPerAction
    )
  }

  @Test fun countRendering_RenderPassPerAction_SimultaneousScenario() {
    runCountingTest(
      COUNT_RENDERINGS,
      SIMULTANEOUS_SCENARIO,
      runtime = RenderPassPerAction
    )
  }

  @Test fun countRendering_RenderPassPerFrame_BaseScenario() {
    runCountingTest(COUNT_RENDERINGS, BASE_SCENARIO, runtime = RenderPassPerFrame())
  }

  @Test fun countRendering_RenderPassPerFrame_HighFrequencyScenario() {
    runCountingTest(
      COUNT_RENDERINGS,
      HIGH_FREQUENCY_SCENARIO,
      runtime = RenderPassPerFrame()
    )
  }

  @Test fun countRendering_RenderPassPerFrame_SimultaneousScenario() {
    runCountingTest(
      COUNT_RENDERINGS,
      SIMULTANEOUS_SCENARIO,
      runtime = RenderPassPerFrame()
    )
  }

  @Test fun countRendering_RenderingPerFrame_BaseScenario() {
    runCountingTest(COUNT_RENDERINGS, BASE_SCENARIO, runtime = RenderingPerFrame())
  }

  @Test fun countRendering_RenderingPerFrame_HighFrequencyScenario() {
    runCountingTest(
      COUNT_RENDERINGS,
      HIGH_FREQUENCY_SCENARIO,
      runtime = RenderingPerFrame()
    )
  }

  @Test fun countRendering_RenderingPerFrame_SimultaneousScenario() {
    runCountingTest(
      COUNT_RENDERINGS,
      SIMULTANEOUS_SCENARIO,
      runtime = RenderingPerFrame()
    )
  }

  private fun runCountingTest(
    testType: TestType,
    scenario: Scenario,
    runtime: RuntimeConfig
  ) {
    val intent = Intent(context, PerformancePoetryActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      if (runtime is RenderPassPerFrame) {
        putExtra(EXTRA_RUNTIME_RENDER_PASS_PER_FRAME, true)
      } else if (runtime is RenderingPerFrame) {
        putExtra(EXTRA_RUNTIME_RENDERING_PER_FRAME, true)
      }
      putExtra(
        EXTRA_CONFIG_SIMULTANEOUS,
        scenario.simultaneousActions
      )
      if (scenario.highFrequencyEventEmitting) {
        putExtra(
          EXTRA_CONFIG_HIGH_FREQ_REPEAT,
          PerformancePoetryActivity.HIGH_FREQUENCY_REPEAT_COUNT
        )
      }
      if (testType == COUNT_RENDERINGS) {
        putExtra(EXTRA_CONFIG_COUNT_RENDERINGS, true)
      }
    }

    if (testType == COUNT_RENDER_PASSES) {
      PerformancePoetryActivity.installedInterceptor = renderPassCountingInterceptor
    }

    PerformancePoetryActivity.renderingsCount = 0
    InstrumentationRegistry.getInstrumentation().context.startActivity(intent)
    device.waitForIdle()
    device.waitForPoetry(10_000)
    device.waitForIdle()

    renderPassCountingInterceptor.reset()

    device.openRavenAndNavigate()

    val expectation = when (runtime) {
      RenderPassPerAction -> scenario.renderPassPerActionExpectation
      is RenderPassPerFrame -> scenario.renderPassPerFrameExpectation
      is RenderingPerFrame -> scenario.renderingPerFrameExpectation
    }

    val title = when (runtime) {
      RenderPassPerAction -> "Runtime: Render Pass Per Action; "
      is RenderPassPerFrame -> "Runtime: Render Pass Per Frame; "
      is RenderingPerFrame -> "Runtime: Rendering Per Frame; "
    } + scenario.title

    when (testType) {
      COUNT_RENDER_PASSES -> checkRenderPasses(expectation, title)
      COUNT_RENDERINGS -> checkRenderings(expectation, title)
    }
  }

  private fun checkRenderings(
    expectation: RenderExpectation,
    title: String
  ) {
    val renderingCount = PerformancePoetryActivity.renderingsCount
    val renderingsSubject = "the number of Renderings emitted from the Workflow runtime" +
      " (lower better)"
    if (renderingCount > expectation.renderings.last) {
      val diff = renderingCount - expectation.renderings.last
      val percentage = "%.2f".format(100.0 * (diff.toDouble() / expectation.renderings.last))
      val by = "$diff ($percentage%)"
      uhOh(
        subject = renderingsSubject,
        by = by,
        value = "$renderingCount",
        oldValue = "${expectation.renderings}",
        scenario = title
      )
    } else if (renderingCount < expectation.renderings.first) {
      val diff = expectation.renderings.first - renderingCount
      val percentage = "%.2f".format(100.0 * (diff.toDouble() / expectation.renderings.first))
      val by = "$diff ($percentage%)"
      congrats(
        subject = renderingsSubject,
        by = by,
        value = "$renderingCount",
        oldValue = "${expectation.renderings}",
        scenario = title
      )
    }
  }

  private fun checkRenderPasses(
    expectation: RenderExpectation,
    title: String
  ) {

    val totalRenderPasses = renderPassCountingInterceptor.renderEfficiencyTracking.totalRenderPasses
    val renderPassSubject = "the number of Render Passes (lower better)"
    if (totalRenderPasses > expectation.totalPasses.last) {
      val diff = totalRenderPasses - expectation.totalPasses.last
      val percentage = "%.2f".format(100.0 * (diff.toDouble() / expectation.totalPasses.last))
      val by = "$diff ($percentage%)"
      uhOh(
        subject = renderPassSubject,
        by = by,
        value = "$totalRenderPasses",
        oldValue = "${expectation.totalPasses}",
        scenario = title
      )
    } else if (totalRenderPasses < expectation.totalPasses.first) {
      val diff = expectation.totalPasses.first - totalRenderPasses
      val percentage = "%.2f".format(100.0 * (diff.toDouble() / expectation.totalPasses.first))
      val by = "$diff ($percentage%)"
      congrats(
        subject = renderPassSubject,
        by = by,
        value = "$totalRenderPasses",
        oldValue = "${expectation.totalPasses}",
        scenario = title
      )
    }

    val freshRenderings =
      renderPassCountingInterceptor.renderEfficiencyTracking.totalNodeStats.nodesRenderedFresh
    val staleRenderings =
      renderPassCountingInterceptor.renderEfficiencyTracking.totalNodeStats.nodesRenderedStale
    val freshRatio = renderPassCountingInterceptor.renderEfficiencyTracking.freshRenderingRatio
    val expectedRatio =
      expectation.freshRenderedNodes.average() /
        (expectation.staleRenderedNodes.average() + expectation.freshRenderedNodes.average())

    val ratioSubject = "the fresh rendering ratio (higher better)"
    val ratioString = "%.3f".format(freshRatio)
    val oldRatioString = "%.3f".format(expectedRatio)
    val valueString = "(ratio: $ratioString; fresh renderings: $freshRenderings;" +
      " stale renderings: $staleRenderings)"
    val oldValueString =
      "(ratio: $oldRatioString; fresh renderings: ${expectation.freshRenderedNodes};" +
        " stale renderings: ${expectation.staleRenderedNodes})"

    if ((freshRatio - expectedRatio) > PERCENTAGE_DIFF) {
      // Something has 'improved' - let's see if we reduced stale nodes.
      if (staleRenderings < expectation.staleRenderedNodes.first) {
        val diff = expectation.staleRenderedNodes.first - staleRenderings
        val percentage =
          "%.2f".format(100.0 * (diff.toDouble() / expectation.staleRenderedNodes.first))
        val by = "rendering $diff fewer stale nodes" +
          " (reducing by $percentage% to $staleRenderings)"
        congrats(
          subject = ratioSubject,
          by = by,
          value = valueString,
          oldValue = oldValueString,
          scenario = title
        )
      } else {
        meh(
          subject = ratioSubject,
          value = valueString,
          oldValue = oldValueString,
          scenario = title
        )
      }
    } else if ((freshRatio - expectedRatio) < -PERCENTAGE_DIFF) {
      // Something has 'worsened' - let's see if we increased stale nodes.
      if (staleRenderings > expectation.staleRenderedNodes.last) {
        val diff = staleRenderings - expectation.staleRenderedNodes.last
        val percentage =
          "%.2f".format(100.0 * (diff.toDouble() / expectation.staleRenderedNodes.last))
        val by = "rendering $diff more stale nodes" +
          " (increasing by $percentage% to $staleRenderings)"
        uhOh(
          subject = ratioSubject,
          by = by,
          value = valueString,
          oldValue = oldValueString,
          scenario = title
        )
      } else {
        meh(
          subject = ratioSubject,
          value = valueString,
          oldValue = oldValueString,
          scenario = title
        )
      }
    }
  }

  companion object {
    const val PERCENTAGE_DIFF = 0.01

    val BASE_SCENARIO = Scenario(
      title = "the base 'Raven navigation scenario'",
      highFrequencyEventEmitting = false,
      simultaneousActions = 0,
      renderPassPerActionExpectation = RenderExpectation(
        renderings = 54..56,
        totalPasses = 54..57,
        freshRenderedNodes = 83..83,
        staleRenderedNodes = 603..603
      ),
      renderPassPerFrameExpectation = RenderExpectation(
        renderings = 42..43,
        totalPasses = 40..41,
        freshRenderedNodes = 83..83,
        staleRenderedNodes = 431..431
      ),
      renderingPerFrameExpectation = RenderExpectation(
        renderings = 29..30,
        totalPasses = 56..56,
        freshRenderedNodes = 83..83,
        staleRenderedNodes = 603..603
      )
    )

    /**
     * Note that when using runtime optimizations that cap on the 'frame rate' the results can
     * be different depending on the H/W device used, because more or less may be able to be
     * processed before the frame times out.
     *
     * We use the values expected in CI for what we commit to the repo, but both are noted here.
     */
    val HIGH_FREQUENCY_SCENARIO = Scenario(
      title = "the 'Raven navigation scenario' WITH high frequency events",
      highFrequencyEventEmitting = true,
      simultaneousActions = 0,
      renderPassPerActionExpectation = RenderExpectation(
        renderings = 179..181,
        totalPasses = 179..181,
        freshRenderedNodes = 213..213,
        staleRenderedNodes = 2348..2348
      ),
      renderPassPerFrameExpectation = RenderExpectation(
        renderings = 50..56, // Pixel 6: 45..48; Nexus CI Emulator: 50..56
        totalPasses = 50..56, // Pixel 6: 45..46; Nexus CI Emulator: 50..56
        freshRenderedNodes = 93..93,
        staleRenderedNodes = 496..496
      ),
      renderingPerFrameExpectation = RenderExpectation(
        renderings = 29..38,
        totalPasses = 181..181,
        freshRenderedNodes = 213..213,
        staleRenderedNodes = 2348..2348
      )
    )

    val SIMULTANEOUS_SCENARIO = Scenario(
      title = "the 'Raven navigation scenario' WITH simultaneous events",
      highFrequencyEventEmitting = false,
      simultaneousActions = 20,
      renderPassPerActionExpectation = RenderExpectation(
        renderings = 56..58,
        totalPasses = 615..637,
        freshRenderedNodes = 123..123,
        staleRenderedNodes = 29775..29775
      ),
      renderPassPerFrameExpectation = RenderExpectation(
        renderings = 39..43,
        totalPasses = 39..43,
        freshRenderedNodes = 123..123,
        staleRenderedNodes = 1971..1971
      ),
      renderingPerFrameExpectation = RenderExpectation(
        renderings = 29..30,
        totalPasses = 636..636,
        freshRenderedNodes = 123..123,
        staleRenderedNodes = 29775..29775
      )
    )

    fun congrats(
      subject: String,
      value: String,
      oldValue: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Congrats! You have improved the $subject ${by?.let { "by $by " } ?: ""}for $scenario!" +
        " Please update the expected value for your config. The value is now $value" +
        " (was $oldValue)."
    )

    fun uhOh(
      subject: String,
      value: String,
      oldValue: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Uh Oh! You have worsened the $subject ${by?.let { "by $by " } ?: ""}for $scenario!" +
        " The value is now $value (was $oldValue). This likely results in worse performance." +
        " You can check with the timing benchmarks if you disagree."
    )

    fun meh(
      subject: String,
      value: String,
      oldValue: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Hmmm. The $subject has improved ${by?.let { "by $by " } ?: ""}for $scenario," +
        " but only because the scenario has changed, impacting expectation" +
        " but not clearly for the better or the worse." +
        " The value is now $value (was $oldValue). Please update the test."
    )
  }
}
