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

import androidx.compose.FrameManager
import androidx.compose.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.foundation.Text
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.findByText
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.compose.bindCompose
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewRegistriesTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun showRendering_recomposes_whenFactoryChanged() {
    val registry1 = ViewRegistry(bindCompose<String> { rendering, _ ->
      Text(rendering)
    })
    val registry2 = ViewRegistry(bindCompose<String> { rendering, _ ->
      Text(rendering.reversed())
    })
    val registry = mutableStateOf(registry1)

    composeRule.setContent {
      registry.value.showRendering("hello", ViewEnvironment(registry.value))
    }

    findByText("hello").assertIsDisplayed()
    FrameManager.framed {
      registry.value = registry2
    }
    findByText("olleh").assertIsDisplayed()
  }
}
