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

import androidx.compose.foundation.Text
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithText
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewEnvironmentsTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun workflowRendering_recomposes_whenFactoryChanged() {
    val registry1 = ViewRegistry(composedViewFactory<String> { rendering, _ ->
      Text(rendering)
    })
    val registry2 = ViewRegistry(composedViewFactory<String> { rendering, _ ->
      Text(rendering.reversed())
    })
    val registry = mutableStateOf(registry1)

    composeRule.setContent {
      WorkflowRendering("hello", ViewEnvironment(registry.value))
    }

    onNodeWithText("hello").assertIsDisplayed()
    registry.value = registry2
    onNodeWithText("olleh").assertIsDisplayed()
  }
}
