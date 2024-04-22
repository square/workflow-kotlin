package com.squareup.benchmarks.performance.complex.poetry.benchmark

import android.content.Context
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.squareup.benchmarks.performance.complex.poetry.benchmark.ComplexPoetryBenchmarks.Companion.PACKAGE_NAME
import com.squareup.benchmarks.performance.complex.poetry.cyborgs.openRavenAndNavigate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

/**
 * Test that will run a scenario to gather a baseline profile for code paths for the Complex Poetry
 * app. See the benchmarks/README for instructions.
 *
 * You will need root access to a physical device to gather the baseline profile.
 */
class ComplexPoetryGatherBaseline {
  @get:Rule val baselineProfileRule: BaselineProfileRule = BaselineProfileRule()

  private lateinit var context: Context
  private lateinit var device: UiDevice

  @Before fun setUp() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    context = ApplicationProvider.getApplicationContext()
    device = UiDevice.getInstance(instrumentation)
  }

  @Test
  fun baselineProfiles() {
    baselineProfileRule.collect(
      packageName = PACKAGE_NAME,
    ) {
      pressHome()
      startActivityAndWait()

      device.openRavenAndNavigate()
    }
  }
}
