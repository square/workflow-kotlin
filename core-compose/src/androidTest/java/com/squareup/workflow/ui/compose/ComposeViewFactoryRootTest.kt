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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.foundation.Text
import androidx.ui.layout.Column
import androidx.ui.semantics.Semantics
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.findByText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ComposeViewFactoryRootTest {

  @Rule @JvmField val composeTestRule = createComposeRule()

  @Test fun safeComposeViewFactoryRoot_wraps_content() {
    val wrapped = ComposeViewFactoryRoot { content ->
      Column {
        Text("Parent")
        content()
      }
    }
    val safeRoot = SafeComposeViewFactoryRoot(wrapped)

    composeTestRule.setContent {
      safeRoot.wrap {
        // Need an explicit semantics container, otherwise both Texts will be merged into a single
        // Semantics object with the text "Parent\nChild".
        Semantics(container = true) {
          Text("Child")
        }
      }
    }

    findByText("Parent")
        .assertIsDisplayed()
    findByText("Child").assertIsDisplayed()
  }

  @Test fun safeComposeViewFactoryRoot_throws_whenChildrenNotInvoked() {
    val wrapped = ComposeViewFactoryRoot { }
    val safeRoot = SafeComposeViewFactoryRoot(wrapped)

    val error = assertFailsWith<IllegalStateException> {
      composeTestRule.setContent {
        safeRoot.wrap {}
      }
    }

    assertThat(error).hasMessageThat()
        .isEqualTo(
            "Expected ComposableDecorator to invoke children exactly once, but was invoked 0 times."
        )
  }

  @Test fun safeComposeViewFactoryRoot_throws_whenChildrenInvokedMultipleTimes() {
    val wrapped = ComposeViewFactoryRoot { children ->
      children()
      children()
    }
    val safeRoot = SafeComposeViewFactoryRoot(wrapped)

    val error = assertFailsWith<IllegalStateException> {
      composeTestRule.setContent {
        safeRoot.wrap {
          Text("Hello")
        }
      }
    }

    assertThat(error).hasMessageThat()
        .isEqualTo(
            "Expected ComposableDecorator to invoke children exactly once, but was invoked 2 times."
        )
  }
}
