package com.squareup.workflow1.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
}
