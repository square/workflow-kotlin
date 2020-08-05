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
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose

import androidx.compose.foundation.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithText
import com.squareup.workflow.Workflow
import com.squareup.workflow.stateless
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowContainerTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun rendersFromViewRegistry() {
    val workflow = Workflow.stateless<Unit, Nothing, String> { "hello" }
    val registry = ViewRegistry(composedViewFactory<String> { rendering, _ -> Text(rendering) })

    composeRule.setContent {
      WorkflowContainer(workflow, ViewEnvironment(registry))
    }

    onNodeWithText("hello").assertIsDisplayed()
  }

  @Test fun automaticallyAddsComposeRenderingFactory() {
    val workflow = Workflow.composed<Unit, Nothing> { _, _, _ ->
      Text("it worked")
    }
    val registry = ViewRegistry()

    composeRule.setContent {
      WorkflowContainer(workflow, ViewEnvironment(registry))
    }

    onNodeWithText("it worked").assertIsDisplayed()
  }
}
