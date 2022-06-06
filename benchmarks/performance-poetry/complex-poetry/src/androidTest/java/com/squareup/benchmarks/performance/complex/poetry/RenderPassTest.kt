package com.squareup.benchmarks.performance.complex.poetry

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_PERF_CONFIG_INITIALIZING
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_PERF_CONFIG_REPEAT
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.openRavenAndNavigate
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.resetToRootPoetryList
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
    val expectedPasses: Int,
    val expectedFreshRenderings: Int,
    val expectedStaleRenderings: Int
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

  @Test fun renderPassCounterComplexWithInitializingState() {
    runRenderPassCounter(COMPLEX_INITIALIZING)
  }

  @Test fun renderPassCounterComplexNoInitializingState() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING)
  }

  @Test fun renderPassCounterComplexNoInitializingStateHighFrequencyEvents() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY)
  }

  private fun runRenderPassCounter(scenario: Scenario) {
    val intent = Intent(context, PerformancePoetryActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(
        EXTRA_PERF_CONFIG_INITIALIZING,
        scenario.useInitializingState
      )
      if (scenario.useHighFrequencyRange) {
        putExtra(EXTRA_PERF_CONFIG_REPEAT, PerformancePoetryActivity.HIGH_FREQUENCY_REPEAT_COUNT)
      }
    }

    InstrumentationRegistry.getInstrumentation().context.startActivity(intent)
    device.waitForPoetry()
    device.waitForIdle()

    // Go back to root list so this is deterministic.
    device.resetToRootPoetryList()

    // Now reset for the actual counting.
    renderPassCountingInterceptor.reset()

    device.openRavenAndNavigate()

    val totalRenderPasses = renderPassCountingInterceptor.renderEfficiencyTracking.totalRenderPasses
    val renderPassSubject = "the number of Render Passes (lower better)"
    if (totalRenderPasses > scenario.expectedPasses) {
      val diff = totalRenderPasses - scenario.expectedPasses
      val percentage = "%.2f".format(100.0 * (diff.toDouble() / scenario.expectedPasses))
      val by = "$diff ($percentage%)"
      uhOh(
        subject = renderPassSubject,
        by = by,
        value = "$totalRenderPasses",
        oldValue = "${scenario.expectedPasses}",
        scenario = scenario.title
      )
    } else if (totalRenderPasses < scenario.expectedPasses) {
      val diff = scenario.expectedPasses - totalRenderPasses
      val percentage = "%.2f".format(100.0 * (diff.toDouble() / scenario.expectedPasses))
      val by = "$diff ($percentage%)"
      congrats(
        subject = renderPassSubject,
        by = by,
        value = "$totalRenderPasses",
        oldValue = "${scenario.expectedPasses}",
        scenario = scenario.title
      )
    }

    val freshRenderings =
      renderPassCountingInterceptor.renderEfficiencyTracking.totalNodeStats.nodesRenderedFresh
    val staleRenderings =
      renderPassCountingInterceptor.renderEfficiencyTracking.totalNodeStats.nodesRenderedStale
    val freshRatio = renderPassCountingInterceptor.renderEfficiencyTracking.freshRenderingRatio
    val expectedRatio =
      scenario.expectedFreshRenderings.toDouble() /
        (scenario.expectedStaleRenderings + scenario.expectedFreshRenderings).toDouble()

    val ratioSubject = "the fresh rendering ratio (higher better)"
    val ratioString = "%.3f".format(freshRatio)
    val oldRatioString = "%.3f".format(expectedRatio)
    val valueString = "(ratio: $ratioString; fresh renderings: $freshRenderings;" +
      " stale renderings: $staleRenderings)"
    val oldValueString =
      "(ratio: $oldRatioString; fresh renderings: ${scenario.expectedFreshRenderings};" +
        " stale renderings: ${scenario.expectedStaleRenderings})"

    if (freshRatio > expectedRatio) {
      // Something has 'improved' - let's see if we reduced stale nodes.
      if (staleRenderings < scenario.expectedStaleRenderings) {
        val diff = scenario.expectedStaleRenderings - staleRenderings
        val percentage =
          "%.2f".format(100.0 * (diff.toDouble() / scenario.expectedStaleRenderings.toDouble()))
        val by = "rendering $diff fewer stale nodes" +
          " (reducing by $percentage% to $staleRenderings)"
        congrats(
          subject = ratioSubject,
          by = by,
          value = valueString,
          oldValue = oldValueString,
          scenario = scenario.title
        )
      } else {
        meh(
          subject = ratioSubject,
          value = valueString,
          oldValue = oldValueString,
          scenario = scenario.title
        )
      }
    } else if (freshRatio < expectedRatio) {
      // Something has 'worsened' - let's see if we increased stale nodes.
      if (staleRenderings > scenario.expectedStaleRenderings) {
        val diff = staleRenderings - scenario.expectedStaleRenderings
        val percentage =
          "%.2f".format(100.0 * (diff.toDouble() / scenario.expectedStaleRenderings.toDouble()))
        val by = "rendering $diff more stale nodes" +
          " (increasing by $percentage% to $staleRenderings)"
        uhOh(
          subject = ratioSubject,
          by = by,
          value = valueString,
          oldValue = oldValueString,
          scenario = scenario.title
        )
      } else {
        meh(
          subject = ratioSubject,
          value = valueString,
          oldValue = oldValueString,
          scenario = scenario.title
        )
      }
    }
  }

  companion object {
    val COMPLEX_INITIALIZING = Scenario(
      title = "the 'Raven navigation with initializing state scenario'",
      useInitializingState = true,
      useHighFrequencyRange = false,
      expectedPasses = 57,
      expectedFreshRenderings = 85,
      expectedStaleRenderings = 608
    )

    val COMPLEX_NO_INITIALIZING = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario'",
      useInitializingState = false,
      useHighFrequencyRange = false,
      expectedPasses = 56,
      expectedFreshRenderings = 83,
      expectedStaleRenderings = 605
    )

    val COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario with high frequency events'",
      useInitializingState = false,
      useHighFrequencyRange = true,
      expectedPasses = 181,
      expectedFreshRenderings = 213,
      expectedStaleRenderings = 2350
    )

    fun congrats(
      subject: String,
      value: String,
      oldValue: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Congrats! You have improved the $subject ${by?.let { "by $by " } ?: ""}in $scenario!" +
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
      "Uh Oh! You have worsened the $subject ${by?.let { "by $by " } ?: ""}in $scenario!" +
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
      "Hmmm. The $subject has improved ${by?.let { "by $by " } ?: ""}in $scenario," +
        " but only because the scenario has changed, impacting expectation" +
        " but not for the better. The value is now $value (was $oldValue). Please update the test."
    )
  }
}
