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
package com.squareup.workflow.ui.compose.internal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.core.AndroidOwner
import androidx.ui.core.ExperimentalLayoutNodeApi
import androidx.ui.foundation.Text
import androidx.ui.layout.Column
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithSubstring
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.onNodeWithText
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.bindShowRendering
import com.squareup.workflow.ui.compose.WorkflowRendering
import com.squareup.workflow.ui.compose.composedViewFactory
import com.squareup.workflow.ui.compose.withCompositionRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ViewFactoriesTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun WorkflowRendering_wrapsFactoryWithRoot_whenAlreadyInComposition() {
    val viewEnvironment = ViewEnvironment(ViewRegistry(TestFactory))
        .withCompositionRoot { content ->
          Column {
            Text("one")
            content()
          }
        }

    composeRule.setContent {
      WorkflowRendering(TestRendering("two"), viewEnvironment)
    }

    onNodeWithText("one", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithText("two", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test fun WorkflowRendering_rendersLegacyAndroidView() {
    val viewEnvironment = ViewEnvironment(ViewRegistry(LegacyViewViewFactory))
        .withCompositionRoot { content ->
          Column {
            Text("one")
            content()
          }
        }

    composeRule.setContent {
      WorkflowRendering(LegacyViewRendering("two"), viewEnvironment)
    }

    onNodeWithText("one", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag(LegacyAndroidViewTestTag, useUnmergedTree = true).assertIsDisplayed()
  }

  @OptIn(ExperimentalLayoutNodeApi::class)
  @Test fun WorkflowRendering_legacyAndroidViewRendersUpdates() {
    // Our view ends up being nested pretty deeply in a bunch of ViewGroups, so iterate through
    // them all until we find it.
    fun AndroidOwner.findTextView() = generateSequence<View>(view as ViewGroup) {
      when (it) {
        is TextView -> null
        is ViewGroup -> it.getChildAt(0)
        else -> error("No text view!")
      }
    }.last() as TextView

    val wrapperText = mutableStateOf("two")
    val viewEnvironment = ViewEnvironment(ViewRegistry(LegacyViewViewFactory))

    composeRule.setContent {
      WorkflowRendering(LegacyViewRendering(wrapperText.value), viewEnvironment)
    }

    val owner = onNodeWithTag(LegacyAndroidViewTestTag, useUnmergedTree = true)
        .fetchSemanticsNode()
        .componentNode
        .owner!! as AndroidOwner

    // Really digging into internals here. This would work better if the accessibility
    // inter-op when creating a LayoutNode from a View existed, so that we could continue
    // to use the standard Compose testing API when dealing with Android views.
    var textView = owner.findTextView()
    assertTrue(textView.text == "two")
    assertTrue(textView.isAttachedToWindow)

    wrapperText.value = "OWT"

    onNodeWithSubstring(
        "Useless - Force View to be updated by using this line to wait."
    ).assertDoesNotExist()

    textView = owner.findTextView()
    assertTrue(textView.text == "OWT")
    assertTrue(textView.isAttachedToWindow)
  }

  private data class TestRendering(val text: String)
  private data class LegacyViewRendering(val text: String)

  private companion object {
    val TestFactory = composedViewFactory<TestRendering> { rendering, _ ->
      Text(rendering.text)
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
