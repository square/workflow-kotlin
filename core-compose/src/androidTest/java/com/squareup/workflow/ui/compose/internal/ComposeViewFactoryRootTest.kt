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
import androidx.ui.layout.Column
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.findByText
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.compose.withComposeViewFactoryRoot
import com.squareup.workflow.ui.compose.wrapWithRootIfNecessary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeViewFactoryRootTest {

  @Rule @JvmField val composeRule = createComposeRule()

  @Test fun wrapWithRootIfNecessary_handlesNoRoot() {
    val viewEnvironment = ViewEnvironment(ViewRegistry())

    composeRule.setContent {
      wrapWithRootIfNecessary(viewEnvironment) {
        Text("foo")
      }
    }

    findByText("foo").assertIsDisplayed()
  }

  @Test fun wrapWithRootIfNecessary_wrapsWhenNecessary() {
    val viewEnvironment = ViewEnvironment(ViewRegistry())
        .withComposeViewFactoryRoot { content ->
          Column {
            Text("one")
            content()
          }
        }

    composeRule.setContent {
      wrapWithRootIfNecessary(viewEnvironment) {
        Text("two")
      }
    }

    findByText("one\ntwo").assertIsDisplayed()
  }

  @Test fun wrapWithRootIfNecessary_onlyWrapsOnce() {
    val viewEnvironment = ViewEnvironment(ViewRegistry())
        .withComposeViewFactoryRoot { content ->
          Column {
            Text("one")
            content()
          }
        }

    composeRule.setContent {
      wrapWithRootIfNecessary(viewEnvironment) {
        Text("two")
        wrapWithRootIfNecessary(viewEnvironment) {
          Text("three")
        }
      }
    }

    findByText("one\ntwo\nthree").assertIsDisplayed()
  }

  @Test fun wrapWithRootIfNecessary_seesUpdatesFromRootWrapper() {
    val wrapperText = mutableStateOf("one")
    val viewEnvironment = ViewEnvironment(ViewRegistry())
        .withComposeViewFactoryRoot { content ->
          Column {
            Text(wrapperText.value)
            content()
          }
        }

    composeRule.setContent {
      wrapWithRootIfNecessary(viewEnvironment) {
        Text("two")
      }
    }

    findByText("one\ntwo").assertIsDisplayed()
    FrameManager.framed {
      wrapperText.value = "ENO"
    }
    findByText("ENO\ntwo").assertIsDisplayed()
  }

  @Test fun wrapWithRootIfNecessary_rewrapsWhenDifferentRoot() {
    val viewEnvironment1 = ViewEnvironment(ViewRegistry())
        .withComposeViewFactoryRoot { content ->
          Column {
            Text("one")
            content()
          }
        }
    val viewEnvironment2 = ViewEnvironment(ViewRegistry())
        .withComposeViewFactoryRoot { content ->
          Column {
            Text("ENO")
            content()
          }
        }
    val viewEnvironment = mutableStateOf(viewEnvironment1)

    composeRule.setContent {
      wrapWithRootIfNecessary(viewEnvironment.value) {
        Text("two")
      }
    }

    findByText("one\ntwo").assertIsDisplayed()
    FrameManager.framed {
      viewEnvironment.value = viewEnvironment2
    }
    findByText("ENO\ntwo").assertIsDisplayed()
  }
}
