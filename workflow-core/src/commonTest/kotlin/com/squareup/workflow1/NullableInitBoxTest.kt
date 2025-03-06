package com.squareup.workflow1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NullableInitBoxTest {

  @Test fun reports_not_initialized() {
    val box = NullableInitBox<String>()

    assertFalse(box.isInitialized)
  }

  @Test fun reports_initialized() {
    val box = NullableInitBox<String>("Hello")

    assertTrue(box.isInitialized)
  }

  @Test fun returns_value() {
    val box = NullableInitBox<String>("Hello")

    assertEquals("Hello", box.getOrThrow())
  }

  @Test fun throws_exceptions() {
    val box = NullableInitBox<String>()

    val exception = assertFailsWith<IllegalStateException> {
      box.getOrThrow()
    }

    assertEquals(
      "NullableInitBox was fetched before it was initialized with a value.",
      exception.message
    )
  }
}
