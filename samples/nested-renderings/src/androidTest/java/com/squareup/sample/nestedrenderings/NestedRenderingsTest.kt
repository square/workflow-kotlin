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
package com.squareup.sample.nestedrenderings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.ui.test.android.AndroidComposeTestRule
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.doClick
import androidx.ui.test.findAllByText
import androidx.ui.test.findByText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ADD_BUTTON_TEXT = "Add Child"

@RunWith(AndroidJUnit4::class)
class NestedRenderingsTest {

  // Launches the activity.
  @Rule @JvmField val composeRule = AndroidComposeTestRule<NestedRenderingsActivity>()

  @Test fun childrenAreAddedAndRemoved() {
    val resetButton = findByText("Reset")

    findByText(ADD_BUTTON_TEXT)
        .assertIsDisplayed()
        .doClick()

    findAllByText(ADD_BUTTON_TEXT)
        .also { addButtons ->
          assertThat(addButtons).hasSize(2)
          addButtons.forEach { it.doClick() }
        }

    findAllByText(ADD_BUTTON_TEXT)
        .also { addButtons ->
          assertThat(addButtons).hasSize(4)
        }

    resetButton.doClick()
    assertThat(findAllByText(ADD_BUTTON_TEXT)).hasSize(1)
  }
}
