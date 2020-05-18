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

package com.squareup.workflow.ui.compose.internal

import androidx.compose.Composable
import com.squareup.workflow.ui.compose.ComposeViewFactoryRoot

/**
 * [ComposeViewFactoryRoot] that asserts that the [wrap] method invokes its children parameter
 * exactly once, and throws an [IllegalStateException] if not.
 */
internal class SafeComposeViewFactoryRoot(
  private val delegate: ComposeViewFactoryRoot
) : ComposeViewFactoryRoot {

  @Composable override fun wrap(content: @Composable() () -> Unit) {
    var childrenCalledCount = 0
    delegate.wrap {
      childrenCalledCount++
      content()
    }
    check(childrenCalledCount == 1) {
      "Expected ComposableDecorator to invoke children exactly once, " +
          "but was invoked $childrenCalledCount times."
    }
  }
}
