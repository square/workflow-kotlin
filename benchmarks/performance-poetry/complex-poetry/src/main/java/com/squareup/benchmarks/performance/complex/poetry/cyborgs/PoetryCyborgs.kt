package com.squareup.benchmarks.performance.complex.poetry.cyborgs

import android.widget.ImageButton
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

const val POETRY_PACKAGE = "com.squareup.benchmarks.performance.complex.poetry"
val PoetryPackageSelector: BySelector =
  By.pkg(POETRY_PACKAGE)
val RavenPoemSelector: BySelector = By.text("The Raven").clickable(true).focusable(true)
val NextSelector: BySelector = By.textStartsWith("next")
val PreviousSelector: BySelector = By.textEndsWith("previous")
val UpButtonSelector: BySelector =
  By.clazz(ImageButton::class.java).descContains("Up").clickable(true)

fun UiDevice.waitForPoetry(timeout: Long = DEFAULT_UI_AUTOMATOR_TIMEOUT) {
  waitFor(PoetryPackageSelector, timeout)
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
    waitForWindowUpdate(POETRY_PACKAGE, DEFAULT_UI_AUTOMATOR_TIMEOUT)
    uiObject = wait(Until.findObject(UpButtonSelector), DEFAULT_UI_AUTOMATOR_TIMEOUT)
  }
}

/**
 * Note this only works in landscape mode.
 */
fun UiDevice.openRavenAndNavigate() {
  waitForIdle()
  try {
    waitForAndClick(RavenPoemSelector)
  } catch (e: IllegalStateException) {
    // may not be at root.
    resetToRootPoetryList()
    waitForAndClick(RavenPoemSelector)
  }
  waitForWindowUpdate(POETRY_PACKAGE, DEFAULT_UI_AUTOMATOR_TIMEOUT)
  waitForAndClick(By.textStartsWith("Deep into that darkness peering"))
  waitForWindowUpdate(POETRY_PACKAGE, DEFAULT_UI_AUTOMATOR_TIMEOUT)

  repeat(5) {
    var fresh = false
    while (!fresh) {
      try {
        fresh = true
        next()
      } catch (e: StaleObjectException) {
        fresh = false
      }
      waitForWindowUpdate(POETRY_PACKAGE, DEFAULT_UI_AUTOMATOR_TIMEOUT)
    }
  }

  repeat(5) {
    var fresh = false
    while (!fresh) {
      try {
        fresh = true
        previous()
      } catch (e: StaleObjectException) {
        fresh = false
      }
      waitForWindowUpdate(POETRY_PACKAGE, DEFAULT_UI_AUTOMATOR_TIMEOUT)
    }
  }

  waitForAndClick(UpButtonSelector)

  // Back to the start.
  waitFor(RavenPoemSelector)
}
