package com.squareup.benchmarks.performance.complex.poetry

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.PERF_CONFIG_EXTRA
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.RenderPassCountingInterceptor
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.SimulatedPerfConfig
import com.squareup.benchmarks.performance.complex.poetry.robots.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.robots.openRavenAndNavigate
import com.squareup.benchmarks.performance.complex.poetry.robots.waitForPoetry
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
    val config: SimulatedPerfConfig,
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
  }

  @Test fun renderPassCounterComplexWithInitializingState() {
    // https://github.com/square/workflow-kotlin/issues/745
    // runRenderPassCounter(COMPLEX_INITIALIZING)
  }

  @Test fun renderPassCounterComplexNoInitializingState() {
    // https://github.com/square/workflow-kotlin/issues/745
    // runRenderPassCounter(COMPLEX_NO_INITIALIZING)
  }

  private fun runRenderPassCounter(scenario: Scenario) {
    val intent = Intent(context, PerformancePoetryActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(PERF_CONFIG_EXTRA, scenario.config)
    }
    device.wakeUp()
    device.pressHome()
    // Do these in landscape so we have both the 'index' and the 'detail' showing.
    device.landscapeOrientation()

    InstrumentationRegistry.getInstrumentation().context.startActivity(intent)

    device.waitForPoetry()
    device.waitForIdle()

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
    val valueString = "(ratio: $ratioString; fresh renderings: $freshRenderings;" +
      " stale renderings: $staleRenderings)"

    if (freshRatio > expectedRatio) {
      // Something has 'improved' - let's see if we reduced stale nodes.
      if (staleRenderings < scenario.expectedStaleRenderings) {
        val diff = scenario.expectedStaleRenderings - staleRenderings
        val percentage =
          "%.2f".format(100.0 * (diff.toDouble() / scenario.expectedStaleRenderings.toDouble()))
        val by = "rendering $diff fewer stale nodes" +
          " (reducing by $percentage% to $staleRenderings)"
        congrats(subject = ratioSubject, by = by, value = valueString, scenario = scenario.title)
      } else {
        meh(ratioSubject, valueString, scenario.title)
      }
    } else if (freshRatio < expectedRatio) {
      // Something has 'worsened' - let's see if we increased stale nodes.
      if (staleRenderings > scenario.expectedStaleRenderings) {
        val diff = staleRenderings - scenario.expectedStaleRenderings
        val percentage =
          "%.2f".format(100.0 * (diff.toDouble() / scenario.expectedStaleRenderings.toDouble()))
        val by = "rendering $diff more stale nodes" +
          " (increasing by $percentage% to $staleRenderings)"
        uhOh(subject = ratioSubject, by = by, value = valueString, scenario = scenario.title)
      } else {
        meh(ratioSubject, valueString, scenario.title)
      }
    }
  }

  companion object {
    val COMPLEX_INITIALIZING = Scenario(
      title = "the 'Raven navigation with initializing state scenario'",
      config = SimulatedPerfConfig(
        isComplex = true,
        useInitializingState = true,
        complexityDelay = 100L
      ),
      expectedPasses = 57,
      expectedFreshRenderings = 83,
      expectedStaleRenderings = 615
    )

    val COMPLEX_NO_INITIALIZING = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario'",
      config = SimulatedPerfConfig(
        isComplex = true,
        useInitializingState = false,
        complexityDelay = 100L
      ),
      expectedPasses = 58,
      expectedFreshRenderings = 84,
      expectedStaleRenderings = 636
    )

    fun congrats(
      subject: String,
      value: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Congrats! You have improved the $subject ${by?.let { "by $by " }}in $scenario!" +
        " Please update the expected value for your config. The value is now $value."
    )

    fun uhOh(
      subject: String,
      value: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Uh Oh! You have worsened the $subject ${by?.let { "by $by " }}in $scenario!" +
        " The value is now $value. This likely results in worse performance." +
        " You can check with the timing benchmarks if you disagree."
    )

    fun meh(
      subject: String,
      value: String,
      scenario: String,
      by: String? = null
    ) = fail(
      "Hmmm. The $subject has improved ${by?.let { "by $by " }}in $scenario," +
        " but only because the scenario has changed, impacting expectation" +
        " but not for the better. The value is now $value. Please update the test."
    )
  }
}
