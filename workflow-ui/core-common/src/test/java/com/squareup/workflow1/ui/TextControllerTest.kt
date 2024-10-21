package com.squareup.workflow1.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(WorkflowUiExperimentalApi::class)
internal class TextControllerTest {

  @Test fun `equals works with the same value`() {
    val controller1 = TextController(initialValue = "apple")
    val controller2 = TextController(initialValue = "apple")
    assertEquals(controller1, controller2)
  }

  @Test fun `equals works with different values`() {
    val controller1 = TextController(initialValue = "apple")
    val controller2 = TextController(initialValue = "orange")
    assertNotEquals(controller1, controller2)
  }
}
