package com.squareup.benchmarks.performance.complex.poetry

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_PERF_CONFIG_REPEAT
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_PERF_CONFIG_SIMULTANEOUS
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_SCENARIO_CONFIG_INITIALIZING
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.openRavenAndNavigate
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.waitForPoetry
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.RenderPassCountingInterceptor
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that is used to confirm that the number of render passes and the fresh rendering ratio
 * is constant.
 */
@RunWith(AndroidJUnit4::class)
class RenderPassTest {
  data class Scenario(
    val title: String,
    val useInitializingState: Boolean,
    val useHighFrequencyRange: Boolean,
    val simultaneousActions: Int,
    val baselineExpectation: RenderExpectation,
  )

  data class RenderExpectation(
    val totalPasses: IntRange,
    val freshRenderedNodes: IntRange,
    val staleRenderedNodes: IntRange
  )

  private val renderPassCountingInterceptor = RenderPassCountingInterceptor()
  private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  private lateinit var context: Context

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    PerformancePoetryActivity.installedInterceptor = renderPassCountingInterceptor

    device.wakeUp()
    device.pressHome()
    device.waitForIdle()

    // Do these in landscape so we have both the 'index' and the 'detail' showing.
    device.landscapeOrientation()
  }

  @Test fun renderPassCounterBaselineComplexWithInitializingState() {
    runRenderPassCounter(COMPLEX_INITIALIZING)
  }

  @Test fun renderPassCounterBaselineComplexNoInitializingState() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING)
  }

  @Test fun renderPassCounterBaselineComplexNoInitializingStateHighFrequencyEvents() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY)
  }

  @Test fun renderPassCounterBaselineComplexNoInitializingStateSimultaneous() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING_SIMULTANEOUS)
  }

  private fun runRenderPassCounter(
    scenario: Scenario,
  ) {
    val intent = Intent(context, PerformancePoetryActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(
        EXTRA_SCENARIO_CONFIG_INITIALIZING,
        scenario.useInitializingState
      )
      putExtra(
        EXTRA_PERF_CONFIG_SIMULTANEOUS,
        scenario.simultaneousActions
      )
      if (scenario.useHighFrequencyRange) {
        putExtra(EXTRA_PERF_CONFIG_REPEAT, PerformancePoetryActivity.HIGH_FREQUENCY_REPEAT_COUNT)
      }
    }

    InstrumentationRegistry.getInstrumentation().context.startActivity(intent)
    device.waitForIdle()
    device.waitForPoetry(10_000)
    device.waitForIdle()

    // Now reset for the actual counting.
    renderPassCountingInterceptor.reset()

    device.openRavenAndNavigate()

    val expectation = scenario.baselineExpectation

    val title = "Runtime: RenderPerAction; " + scenario.title

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

    if ((freshRatio - expectedRatio) > 0.05) {
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
    } else if ((freshRatio - expectedRatio) < -0.05) {
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
    val COMPLEX_INITIALIZING = Scenario(
      title = "the 'Raven navigation with initializing state scenario'",
      useInitializingState = true,
      useHighFrequencyRange = false,
      simultaneousActions = 0,
      baselineExpectation = RenderExpectation(
        totalPasses = 57..57,
        freshRenderedNodes = 85..85,
        staleRenderedNodes = 608..608
      ),
    )

    val COMPLEX_NO_INITIALIZING = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario'",
      useInitializingState = false,
      useHighFrequencyRange = false,
      simultaneousActions = 0,
      baselineExpectation = RenderExpectation(
        totalPasses = 56..56,
        freshRenderedNodes = 83..83,
        staleRenderedNodes = 605..605
      ),
    )

    val COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario with high frequency events'",
      useInitializingState = false,
      useHighFrequencyRange = true,
      simultaneousActions = 0,
      baselineExpectation = RenderExpectation(
        totalPasses = 181..181,
        freshRenderedNodes = 213..213,
        staleRenderedNodes = 2350..2350
      ),
    )

    val COMPLEX_NO_INITIALIZING_SIMULTANEOUS = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario with simultaneous events" +
        " AND high frequency events'",
      useInitializingState = false,
      useHighFrequencyRange = true,
      simultaneousActions = 20,
      baselineExpectation = RenderExpectation(
        totalPasses = 762..762,
        freshRenderedNodes = 253..253,
        staleRenderedNodes = 38919..38919
      ),
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
