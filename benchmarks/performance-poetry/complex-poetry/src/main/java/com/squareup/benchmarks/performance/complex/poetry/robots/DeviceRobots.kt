package com.squareup.benchmarks.performance.complex.poetry.robots

import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream

fun UiDevice.waitForAndClick(
  selector: BySelector,
  timeout: Long = 3_000
) {
  val uiObject = wait(Until.findObject(selector), timeout)
  check(uiObject != null) {
    "Waited $timeout ms for $selector and could not find it in window\n" +
      windowHierarchy()
  }
  uiObject.click()
}

fun UiDevice.waitFor(
  selector: BySelector,
  timeout: Long = 3_000
): UiObject2 {
  val uiObject = wait(Until.findObject(selector), timeout)
  check(uiObject != null) {
    "Waited $timeout ms for $selector and could not find it in window\n" +
      windowHierarchy()
  }
  return uiObject
}

fun UiDevice.windowHierarchy(): String? = ByteArrayOutputStream().run {
  dumpWindowHierarchy(this)
  toString("UTF-8")
}

fun UiDevice.landscapeOrientation() {
  // Landscape Orientation.
  unfreezeRotation()

  setOrientationRight()
}
