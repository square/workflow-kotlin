package com.squareup.sample.compose.multiplatform

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test

class ExampleTest {

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun myTest() = runComposeUiTest {
    // Declares a mock UI to demonstrate API calls
    //
    // Replace with your own declarations to test the code of your project
    setContent {
      Column {
        var text by remember { mutableStateOf("Hello") }
        Text(
          text = text,
          modifier = Modifier.testTag("text")
        )
        Button(
          onClick = { text = "Compose" },
          modifier = Modifier.testTag("button")
        ) {
          Text("Click me")
        }
      }
    }

    // Tests the declared UI with assertions and actions of the Compose Multiplatform testing API
    onNodeWithTag("text").assertTextEquals("Hello")
    onNodeWithTag("button").performClick()
    onNodeWithTag("text").assertTextEquals("Compose")
  }
}
