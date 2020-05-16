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
import androidx.ui.test.SemanticsNodeInteraction
import androidx.ui.test.SemanticsNodeInteractionCollection
import androidx.ui.test.android.AndroidComposeTestRule
import androidx.ui.test.assertCountEquals
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.doClick
import androidx.ui.test.findAllByText
import androidx.ui.test.findByText
import androidx.ui.test.last
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ADD_BUTTON_TEXT = "Add Child"

@RunWith(AndroidJUnit4::class)
class NestedRenderingsTest {

  // Launches the activity.
  @Rule @JvmField val composeRule = AndroidComposeTestRule<NestedRenderingsActivity>()

  @Test fun childrenAreAddedAndRemoved() {
    findByText(ADD_BUTTON_TEXT)
        .assertIsDisplayed()
        .doClick()

    findAllByText(ADD_BUTTON_TEXT)
        .assertCountEquals(2)
        .forEach { it.doClick() }

    findAllByText(ADD_BUTTON_TEXT)
        .assertCountEquals(4)

    findAllByText("Reset").last()
        .doClick()
    findAllByText(ADD_BUTTON_TEXT).assertCountEquals(1)
  }

  private fun SemanticsNodeInteractionCollection.forEach(
    block: (SemanticsNodeInteraction) -> Unit
  ) {
    val count = fetchSemanticsNodes().size
    for (i in 0 until count) block(get(i))
  }
}
