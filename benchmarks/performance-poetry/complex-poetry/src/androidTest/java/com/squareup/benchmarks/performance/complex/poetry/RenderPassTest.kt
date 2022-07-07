package com.squareup.benchmarks.performance.complex.poetry

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_PERF_CONFIG_INITIALIZING
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_PERF_CONFIG_REPEAT
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_RUNTIME_COMPOSE_RUNTIME
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoetryActivity.Companion.EXTRA_RUNTIME_FRAME_TIMEOUT
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.landscapeOrientation
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.openRavenAndNavigate
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.resetToRootPoetryList
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.waitForPoetry
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.RenderPassCountingInterceptor
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
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
    val baselineExpectation: RenderExpectation,
    val frameTimeoutExpectation: RenderExpectation,
    val frameTimeoutComposeExpectation: RenderExpectation
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

  @Test fun renderPassCounterFrameTimeoutComplexWithInitializingState() {
    runRenderPassCounter(COMPLEX_INITIALIZING, useFrameTimeout = true)
  }

  @Test fun renderPassCounterFrameTimeoutComplexNoInitializingState() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING, useFrameTimeout = true)
  }

  @Test fun renderPassCounterFrameTimeoutComplexNoInitializingStateHighFrequencyEvents() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY, useFrameTimeout = true)
  }

  @Ignore(
    "Not sure why but this gets stuck on initializing. Compose doesn't get the next" +
      " frame when this is started by the test, but it does when running directly."
  )
  @Test fun renderPassCounterFrameTimeoutComposeComplexWithInitializingState() {
    runRenderPassCounter(COMPLEX_INITIALIZING, useFrameTimeout = true, useCompose = true)
  }

  @Test fun renderPassCounterFrameTimeoutComposeComplexNoInitializingState() {
    runRenderPassCounter(COMPLEX_NO_INITIALIZING, useFrameTimeout = true, useCompose = true)
  }

  @Test fun renderPassCounterFrameTimeoutComposeComplexNoInitializingStateHighFrequencyEvents() {
    runRenderPassCounter(
      COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY,
      useFrameTimeout = true,
      useCompose = true
    )
  }

  private fun runRenderPassCounter(
    scenario: Scenario,
    useFrameTimeout: Boolean = false,
    useCompose: Boolean = false
  ) {
    if (useCompose) require(useFrameTimeout) { "Only use Compose Frame Timeout." }

    val intent = Intent(context, PerformancePoetryActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(
        EXTRA_PERF_CONFIG_INITIALIZING,
        scenario.useInitializingState
      )
      putExtra(EXTRA_RUNTIME_FRAME_TIMEOUT, useFrameTimeout)
      if (useFrameTimeout) {
        putExtra(EXTRA_RUNTIME_COMPOSE_RUNTIME, useCompose)
      }
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

    val expectation =
      if (useFrameTimeout) {
        if (useCompose) {
          scenario.frameTimeoutComposeExpectation
        } else {
          scenario.frameTimeoutExpectation
        }
      } else {
        scenario.baselineExpectation
      }

    val title = if (useFrameTimeout) {
      val usingCompose = if (useCompose) "(Using Compose Optimizations)" else "(No Compose)"
      "Runtime: FrameTimeout $usingCompose;"
    } else {
      "Runtime: RenderPerAction; "
    } + scenario.title

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
      baselineExpectation = RenderExpectation(
        totalPasses = 57..57,
        freshRenderedNodes = 85..85,
        staleRenderedNodes = 608..608
      ),
      frameTimeoutExpectation = RenderExpectation(
        totalPasses = 41..42,
        freshRenderedNodes = 85..85,
        staleRenderedNodes = 436..436
      ),
      frameTimeoutComposeExpectation = RenderExpectation(
        totalPasses = 41..42,
        freshRenderedNodes = 85..85,
        staleRenderedNodes = 436..436
      )
    )

    val COMPLEX_NO_INITIALIZING = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario'",
      useInitializingState = false,
      useHighFrequencyRange = false,
      baselineExpectation = RenderExpectation(
        totalPasses = 56..56,
        freshRenderedNodes = 83..83,
        staleRenderedNodes = 605..605
      ),
      frameTimeoutExpectation = RenderExpectation(
        totalPasses = 40..41,
        freshRenderedNodes = 83..83,
        staleRenderedNodes = 431..431
      ),
      frameTimeoutComposeExpectation = RenderExpectation(
        totalPasses = 40..41,
        freshRenderedNodes = 113..113,
        staleRenderedNodes = 82..82
      )
    )

    /**
     * Note that for this scenario the frame timeout runtime can have different results
     * depending on what device/emulator it is run on.
     *
     * Physical Pixel 6:
     * ```
     RenderExpectation(
     totalPasses = 56..61,
     freshRenderedNodes = 106..108,
     staleRenderedNodes = 679..698
     )
     * ```
     * We use the values expected in CI for what we commit to the repo.
     */
    val COMPLEX_NO_INITIALIZING_HIGH_FREQUENCY = Scenario(
      title = "the 'Raven navigation (no initializing state) scenario with high frequency events'",
      useInitializingState = false,
      useHighFrequencyRange = true,
      baselineExpectation = RenderExpectation(
        totalPasses = 181..181,
        freshRenderedNodes = 213..213,
        staleRenderedNodes = 2350..2350
      ),
      frameTimeoutExpectation = RenderExpectation(
        totalPasses = 60..64,
        freshRenderedNodes = 106..108,
        staleRenderedNodes = 679..698
      ),
      frameTimeoutComposeExpectation = RenderExpectation(
        totalPasses = 59..64,
        freshRenderedNodes = 259..259,
        staleRenderedNodes = 207..207
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
