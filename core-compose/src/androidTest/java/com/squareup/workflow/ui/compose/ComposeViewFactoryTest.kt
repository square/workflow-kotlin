/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.ui.compose

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithText
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.WorkflowViewStub
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeViewFactoryTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun wrapsFactoryWithRoot() {
    val wrapperText = mutableStateOf("one")
    val viewEnvironment = ViewEnvironment(ViewRegistry(TestFactory))
        .withCompositionRoot { content ->
          Column {
            Text(wrapperText.value)
            content()
          }
        }

    composeRule.setContent {
      AndroidView(::RootView) {
        it.setViewEnvironment(viewEnvironment)
      }
    }

    // Compose bug doesn't let us use assertIsDisplayed on older devices.
    // See https://issuetracker.google.com/issues/157728188.
    composeRule.onNodeWithText("one").assertExists()
    composeRule.onNodeWithText("two").assertExists()

    wrapperText.value = "ENO"

    composeRule.onNodeWithText("ENO").assertExists()
    composeRule.onNodeWithText("two").assertExists()
  }

  private class RootView(context: Context) : FrameLayout(context) {
    private val stub = WorkflowViewStub(context).also(::addView)

    fun setViewEnvironment(viewEnvironment: ViewEnvironment) {
      stub.update(TestRendering("two"), viewEnvironment)
    }
  }

  private data class TestRendering(val text: String)

  private companion object {
    val TestFactory = composedViewFactory<TestRendering> { rendering, _ ->
      Text(rendering.text)
    }
  }
}
