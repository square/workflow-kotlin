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
package com.squareup.workflow1.ui.compose.internal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.composedViewFactory
import com.squareup.workflow1.ui.compose.withCompositionRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class ViewFactoriesTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun WorkflowRendering_wrapsFactoryWithRoot_whenAlreadyInComposition() {
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to ViewRegistry(TestFactory)))
        .withCompositionRoot { content ->
          Column {
            BasicText("one")
            content()
          }
        }

    composeRule.setContent {
      WorkflowRendering(TestRendering("two"), viewEnvironment)
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun WorkflowRendering_legacyAndroidViewRendersUpdates() {
    val wrapperText = mutableStateOf("two")
    val viewEnvironment =
      ViewEnvironment(mapOf(ViewRegistry to ViewRegistry(LegacyViewViewFactory)))

    composeRule.setContent {
      WorkflowRendering(LegacyViewRendering(wrapperText.value), viewEnvironment)
    }

    onView(withText("two")).check(matches(isDisplayed()))
    wrapperText.value = "OWT"
    onView(withText("OWT")).check(matches(isDisplayed()))
  }

  private data class TestRendering(val text: String)
  private data class LegacyViewRendering(val text: String)

  private companion object {
    val TestFactory = composedViewFactory<TestRendering> { rendering, _ ->
      BasicText(rendering.text)
    }
    val LegacyViewViewFactory = object : ViewFactory<LegacyViewRendering> {
      override val type = LegacyViewRendering::class

      override fun buildView(
        initialRendering: LegacyViewRendering,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup?
      ): View {
        return TextView(contextForNewView).apply {
          bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
            text = rendering.text
          }
        }
      }
    }
  }
}
