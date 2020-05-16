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

import android.view.ViewGroup
import androidx.compose.Composable
import androidx.compose.CompositionReference
import androidx.compose.Recomposer
import androidx.compose.compositionReference
import androidx.compose.currentComposer
import androidx.ui.core.setContent
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
internal data class ParentComposition(
  val reference: CompositionReference? = null,
  val recomposer: Recomposer? = null
) {
  companion object : ViewEnvironmentKey<ParentComposition>(ParentComposition::class) {
    override val default: ParentComposition get() = ParentComposition()
  }
}

/**
 * Creates a [ParentComposition] from the current point in the composition and adds it to this
 * [ViewEnvironment].
 */
@Composable internal fun ViewEnvironment.withParentComposition(): ViewEnvironment {
  val compositionReference = ParentComposition(
      reference = compositionReference(),
      recomposer = currentComposer.recomposer
  )
  return this + (ParentComposition to compositionReference)
}

/**
 * Starts composing [content] into this [ViewGroup].
 *
 * If there is a [ParentComposition] present in [initialViewEnvironment], it will start the
 * composition as a subcomposition of that continuation.
 *
 * This function corresponds to [withParentComposition].
 */
internal fun ViewGroup.setOrContinueContent(
  initialViewEnvironment: ViewEnvironment,
  content: @Composable() () -> Unit
) {
  val (compositionReference, recomposer) = initialViewEnvironment[ParentComposition]
  if (compositionReference != null && recomposer != null) {
    // Somewhere above us in the workflow rendering tree, there's another bindCompose factory.
    // We need to link to its composition reference so we inherit its ambients.
    setContent(recomposer, compositionReference, content)
  } else {
    // This is the first bindCompose factory in the rendering tree, so it won't be a child
    // composition.
    setContent(Recomposer.current(), content)
  }
}
