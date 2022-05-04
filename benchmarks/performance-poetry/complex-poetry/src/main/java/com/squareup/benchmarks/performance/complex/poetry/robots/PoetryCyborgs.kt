package com.squareup.benchmarks.performance.complex.poetry.robots

import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val POETRY_PACKAGE = "com.squareup.benchmarks.performance.complex.poetry"
val PoetryPackageSelector: BySelector =
  By.pkg(POETRY_PACKAGE).depth(0)
val LoadingDialogSelector: BySelector =
  By.clazz(ProgressBar::class.java).res(POETRY_PACKAGE, "loading_progress_bar")
val RavenPoemSelector: BySelector = By.text("The Raven").clickable(true).focusable(true)
val NextSelector: BySelector = By.textStartsWith("next")
val PreviousSelector: BySelector = By.textEndsWith("previous")
val UpButtonSelector: BySelector =
  By.clazz(ImageButton::class.java).descContains("Up").clickable(true)

fun UiDevice.waitForLoadingInterstitial(timeout: Long = DEFAULT_UI_AUTOMATOR_TIMEOUT) {
  wait(Until.hasObject(LoadingDialogSelector), timeout)
}

fun UiDevice.waitForPoetry(timeout: Long = DEFAULT_UI_AUTOMATOR_TIMEOUT) {
  wait(Until.hasObject(PoetryPackageSelector), timeout)
}

fun UiDevice.next(timeout: Long = DEFAULT_UI_AUTOMATOR_TIMEOUT) {
  waitForAndClick(NextSelector, timeout)
}

fun UiDevice.previous(timeout: Long = DEFAULT_UI_AUTOMATOR_TIMEOUT) {
  waitForAndClick(PreviousSelector, timeout)
}

fun UiDevice.resetToRootPoetryList() {
  var uiObject = wait(Until.findObject(UpButtonSelector), DEFAULT_UI_AUTOMATOR_TIMEOUT)
  while (uiObject != null) {
    uiObject.click()
    waitForLoadingInterstitial()
    uiObject = wait(Until.findObject(UpButtonSelector), DEFAULT_UI_AUTOMATOR_TIMEOUT)
  }
}

/**
 * Note this only works in landscape mode.
 */
fun UiDevice.openRavenAndNavigate() {
  waitForIdle()
  waitForAndClick(RavenPoemSelector)
  waitForLoadingInterstitial()
  waitForAndClick(By.textStartsWith("Deep into that darkness peering"))
  waitForLoadingInterstitial()

  repeat(5) {
    next()
    waitForLoadingInterstitial()
  }

  repeat(5) {
    previous()
    waitForLoadingInterstitial()
  }

  waitForAndClick(UpButtonSelector)
  waitForLoadingInterstitial()

  // Back to the start.
  waitFor(RavenPoemSelector)
}
