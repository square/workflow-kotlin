package com.squareup.sample.dungeon.benchmark

import android.content.Context
import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shamelessly copied from the example the Google Benchmark team made on the Lottie library
 * https://github.com/airbnb/lottie-android/pull/2005.
 *
 * This test actually generates the profiles.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalBaselineProfilesApi::class)
class DungeonGatherBaselineProfile {

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
    baselineProfileRule.collectBaselineProfile(
      packageName = PACKAGE_NAME,
    ) {
      pressHome()
      startActivityAndWait()
      openMazeAndNavigate(device)
    }
  }

  companion object {

    fun openMazeAndNavigate(device: UiDevice) {
      val boardsList =
        UiScrollable(UiSelector().className("androidx.recyclerview.widget.RecyclerView"))
      boardsList.waitForExists(UI_TIMEOUT_MS)
      val mazeBoardSelector = UiSelector().enabled(true).clickable(true).instance(1)
      val mazeBoard = boardsList.getChild(mazeBoardSelector)
      mazeBoard.waitForExists(UI_TIMEOUT_MS)
      mazeBoard.clickAndWaitForNewWindow()

      val leftButtonSelector = UiSelector()
        .resourceId("$PACKAGE_NAME:id/move_left")
      val leftButton = device.findObject(leftButtonSelector)
      leftButton.waitForExists(UI_TIMEOUT_MS)
      repeat(2) {
        leftButton.click()
      }
    }

    const val PACKAGE_NAME: String = "com.squareup.sample.dungeon"
    const val UI_TIMEOUT_MS: Long = 2000L
  }
}
