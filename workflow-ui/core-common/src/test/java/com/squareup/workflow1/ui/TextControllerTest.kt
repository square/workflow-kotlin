package com.squareup.workflow1.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(WorkflowUiExperimentalApi::class)
internal class TextControllerTest {

  @Test fun `does not emit initial value`() = runTest {
    val controller = TextController()
    controller.onTextChanged.test {
      expectNoEvents()
    }
  }

  @Test fun `emits value when text changes`() = runTest {
    val controller = TextController()
    controller.onTextChanged.test {
      controller.textValue = "apple"
      assertThat(awaitItem()).isEqualTo("apple")
      controller.textValue = "orange"
      assertThat(awaitItem()).isEqualTo("orange")
    }
  }

  @Test fun `does not emit twice with the same value`() = runTest {
    val controller = TextController()
    controller.onTextChanged.test {
      controller.textValue = "apple"
      assertThat(awaitItem()).isEqualTo("apple")
      controller.textValue = "apple"
      expectNoEvents()
    }
  }

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
