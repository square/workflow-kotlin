package com.squareup.sample.dungeon.benchmark

import android.content.Context
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.sample.dungeon.benchmark.DungeonGatherBaselineProfile.Companion.PACKAGE_NAME
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
class DungeonStartupBenchmark {

  @get:Rule val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

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

  @Test fun benchmarkStartupBaselineProfiles() {
    benchmarkStartup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))
  }

  @Test fun benchmarkStartupFullAOT() {
    benchmarkStartup(CompilationMode.Full())
  }

  @OptIn(ExperimentalTime::class) fun benchmarkStartup(compilationMode: CompilationMode) {
    benchmarkRule.measureRepeated(
      packageName = PACKAGE_NAME,
      metrics = listOf(StartupTimingMetric()),
      iterations = 3,
      startupMode = StartupMode.COLD,
      compilationMode = compilationMode,
      setupBlock = {
        pressHome()
      }
    ) {
      startActivityAndWait()
      DungeonGatherBaselineProfile.openMazeAndNavigate(device)
    }
  }
}
