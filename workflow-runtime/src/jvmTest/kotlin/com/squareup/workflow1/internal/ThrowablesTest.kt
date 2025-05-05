package com.squareup.workflow1.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class ThrowablesTest {

  @Test fun `requireWithKey throws IllegalArgumentException`() {
    try {
      requireWithKey(false, "requiredKey") { "message" }
    } catch (e: IllegalArgumentException) {
      assertEquals("message", e.message)
      e.assertIsKeyedException("requiredKey")
      return
    }
  }

  @Test fun `checkWithKey throws IllegalStateException`() {
    try {
      checkWithKey(false, "checkedKey") { "message" }
    } catch (e: IllegalStateException) {
      assertEquals("message", e.message)
      e.assertIsKeyedException("checkedKey")
      return
    }
  }

  @Test fun `Throwable withKey adds frame based on key`() {
    RuntimeException("cause").withKey("key").assertIsKeyedException("key")
  }

  private fun RuntimeException.assertIsKeyedException(key: String) {
    val top = stackTrace[0]
    val topPlusOne = stackTrace[1]
    assertEquals(topPlusOne.className, top.className, "Uses real class name")
    assertEquals(key, top.fileName)
    assertEquals(key.hashCode(), top.lineNumber)
  }
}
