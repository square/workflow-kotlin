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
import androidx.ui.core.setContent
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewEnvironmentKey

/**
 * Holds a [CompositionReference] and that can be passed to [setOrSubcomposeContent] to create a
 * composition that is a child of another composition. Subcompositions get ambients and other
 * compose context from their parent, and propagate invalidations, which allows ambients provided
 * around a [showRendering] call to be read by nested Compose-based view factories.
 *
 * When [showRendering] is called, it will store an instance of this class in the [ViewEnvironment].
 * [ComposeViewFactory] pulls the reference out of the environment and uses it to link its
 * composition to the outer one.
 */
internal class ParentComposition(
  var reference: CompositionReference? = null
) {
  companion object : ViewEnvironmentKey<ParentComposition>(ParentComposition::class) {
    override val default: ParentComposition get() = ParentComposition()
  }
}

/**
 * Creates a [ParentComposition] from the current point in the composition and adds it to this
 * [ViewEnvironment].
 */
@Composable internal fun ViewEnvironment.withParentComposition(
  reference: CompositionReference = compositionReference()
): ViewEnvironment {
  val compositionReference = ParentComposition(reference = reference)
  return this + (ParentComposition to compositionReference)
}

/**
 * Starts composing [content] into this [ViewGroup].
 *
 * If [parentComposition] is not null, [content] will be installed as a _subcomposition_ of the
 * parent composition, meaning that it will propagate ambients and invalidation.
 *
 * This function corresponds to [withParentComposition].
 */
internal fun ViewGroup.setOrSubcomposeContent(
  parentComposition: CompositionReference?,
  content: @Composable() () -> Unit
) {
  if (parentComposition != null) {
    // Somewhere above us in the workflow rendering tree, there's another bindCompose factory.
    // We need to link to its composition reference so we inherit its ambients.
    setContent(Recomposer.current(), parentComposition, content)
  } else {
    // This is the first bindCompose factory in the rendering tree, so it won't be a child
    // composition.
    setContent(Recomposer.current(), content)
  }
}
