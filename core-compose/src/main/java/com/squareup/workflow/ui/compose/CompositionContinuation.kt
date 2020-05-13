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

import androidx.compose.Composable
import androidx.compose.CompositionReference
import androidx.compose.Recomposer
import androidx.compose.compositionReference
import androidx.compose.currentComposer
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewEnvironmentKey

/**
 * Holds a [CompositionReference] and a [Recomposer] that can be used to [setContent] to create a
 * composition that is a child of another composition. Child compositions get ambients and other
 * compose context from their parent, which allows ambients provided around a [showRendering] call
 * to be read by nested [bindCompose] factories.
 *
 * When [showRendering] is called, it will store an instance of this class in the [ViewEnvironment].
 * [ComposeViewFactory] will then pull the continuation out of the environment and use it to link
 * its composition to the outer one.
 */
internal data class CompositionContinuation(
  val reference: CompositionReference? = null,
  val recomposer: Recomposer? = null
) {
  companion object : ViewEnvironmentKey<CompositionContinuation>(
      CompositionContinuation::class
  ) {
    override val default: CompositionContinuation
      get() = CompositionContinuation()
  }
}

/**
 * Creates a [CompositionContinuation] from the current point in the composition and adds it to this
 * [ViewEnvironment].
 */
@Composable internal fun ViewEnvironment.withCompositionContinuation(): ViewEnvironment {
  val compositionReference = CompositionContinuation(
      reference = compositionReference(),
      recomposer = currentComposer.recomposer
  )
  return this + (CompositionContinuation to compositionReference)
}
