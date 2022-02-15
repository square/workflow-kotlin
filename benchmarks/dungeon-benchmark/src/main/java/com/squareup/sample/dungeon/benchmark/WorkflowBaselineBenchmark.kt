package com.squareup.sample.dungeon.benchmark

import android.content.Context
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.sample.dungeon.benchmark.WorkflowBaselineProfiles.Companion.PACKAGE_NAME
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 * Shamelessly copied from the example the Google Benchmark team made on the Lottie library
 * https://github.com/airbnb/lottie-android/pull/2005.
 *
 * This test compares performance with and without profiles.
 */
@RunWith(AndroidJUnit4::class)
public class WorkflowBaselineBenchmark {

  @get:Rule
  public val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

  private lateinit var context: Context
  private lateinit var device: UiDevice

  @Before
  public fun setUp() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    context = ApplicationProvider.getApplicationContext()
    device = UiDevice.getInstance(instrumentation)
  }

  @Test
  public fun benchmarkNoCompilation() {
    benchmark(CompilationMode.None())
  }

  @Test
  public fun benchmarkBaselineProfiles() {
    benchmark(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))
  }

  @OptIn(ExperimentalTime::class)
  public fun benchmark(compilationMode: CompilationMode) {
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
      iterations = 3,
      startupMode = StartupMode.COLD,
      compilationMode = compilationMode,
      setupBlock = {
        pressHome()
      }
    ) {
      startActivityAndWait()
      WorkflowBaselineProfiles.openMazeAndNavigate(device)
    }
  }
}
