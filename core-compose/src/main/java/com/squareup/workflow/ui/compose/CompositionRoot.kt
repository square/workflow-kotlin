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

package com.squareup.workflow.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.remember
import androidx.compose.staticAmbientOf
import com.squareup.workflow.ui.compose.internal.mapFactories
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Used by [wrapWithRootIfNecessary] to ensure the [CompositionRoot] is only applied once.
 */
private val HasViewFactoryRootBeenApplied = staticAmbientOf { false }

/**
 * A `@Composable` function that will be used to wrap the first (highest-level)
 * [composedViewFactory] view factory in a composition. This can be used to setup any ambients that
 * all [composedViewFactory] factories need access to, such as e.g. UI themes.
 *
 * This function will called once, to wrap the _highest-level_ [composedViewFactory] in the tree.
 * However, ambients are propagated down to child [composedViewFactory] compositions, so any
 * ambients provided here will be available in _all_ [composedViewFactory] compositions.
 */
typealias CompositionRoot = @Composable() (content: @Composable() () -> Unit) -> Unit

/**
 * Convenience function for applying a [CompositionRoot] to this [ViewEnvironment]'s [ViewRegistry].
 * See [ViewRegistry.withCompositionRoot].
 */
@WorkflowUiExperimentalApi
fun ViewEnvironment.withCompositionRoot(root: CompositionRoot): ViewEnvironment =
  this + (ViewRegistry to this[ViewRegistry].withCompositionRoot(root))

/**
 * Returns a [ViewRegistry] that ensures that any [composedViewFactory] factories registered in this
 * registry will be wrapped exactly once with a [CompositionRoot] wrapper.
 * See [CompositionRoot] for more information.
 */
@WorkflowUiExperimentalApi
fun ViewRegistry.withCompositionRoot(root: CompositionRoot): ViewRegistry =
  mapFactories { factory ->
    @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
    factory as? ComposeViewFactory<Any> ?: return@mapFactories factory

    @Suppress("UNCHECKED_CAST")
    ComposeViewFactory(factory.type) { rendering, environment ->
      wrapWithRootIfNecessary(root) {
        factory.content(rendering, environment)
      }
    }
  }

/**
 * Adds [content] to the composition, ensuring that [CompositionRoot] has been applied. Will only
 * wrap the content at the highest occurrence of this function in the composition subtree.
 */
@VisibleForTesting(otherwise = PRIVATE)
@Composable internal fun wrapWithRootIfNecessary(
  root: CompositionRoot,
  content: @Composable() () -> Unit
) {
  if (HasViewFactoryRootBeenApplied.current) {
    // The only way this ambient can have the value true is if, somewhere above this point in the
    // composition, the else case below was hit and wrapped us in the ambient. Since the root
    // wrapper will have already been applied, we can just compose content directly.
    content()
  } else {
    // If the ambient is false, this is the first time this function has appeared in the composition
    // so far. We provide a true value for the ambient for everything below us, so any recursive
    // calls to this function will hit the if case above and not re-apply the wrapper.
    Providers(HasViewFactoryRootBeenApplied provides true) {
      val safeRoot: CompositionRoot = remember(root) { safeCompositionRoot(root) }
      safeRoot(content)
    }
  }
}

/**
 * [CompositionRoot] that asserts that the content method invokes its children parameter
 * exactly once, and throws an [IllegalStateException] if not.
 */
internal fun safeCompositionRoot(delegate: CompositionRoot): CompositionRoot = { content ->
  var childrenCalledCount = 0
  delegate {
    childrenCalledCount++
    content()
  }
  check(childrenCalledCount == 1) {
    "Expected ComposableDecorator to invoke children exactly once, " +
        "but was invoked $childrenCalledCount times."
  }
}
