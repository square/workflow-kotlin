/*
 * Copyright 2019 Square Inc.
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
// See https://youtrack.jetbrains.com/issue/KT-31734
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.Composable
import androidx.compose.FrameManager
import androidx.compose.Recomposer
import androidx.compose.StructurallyEqual
import androidx.compose.mutableStateOf
import androidx.ui.core.setContent
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.bindShowRendering
import kotlin.reflect.KClass

/**
 * Creates a [ViewFactory] that uses a [Composable] function to display the rendering.
 *
 * Note that the function you pass in will not have any `MaterialTheme` applied, so views that rely
 * on Material theme attributes must be explicitly wrapped with `MaterialTheme`.
 *
 * Simple usage:
 *
 * ```
 * // Function references to @Composable functions aren't supported yet.
 * val FooBinding = bindCompose { showFoo(it) }
 *
 * @Composable
 * private fun showFoo(foo: FooRendering) {
 *   MaterialTheme {
 *     Text(foo.message)
 *   }
 * }
 *
 * …
 *
 * val viewRegistry = ViewRegistry(FooBinding, …)
 * ```
 *
 * ## Nesting child renderings
 *
 * Workflows can render other workflows, and renderings from one workflow can contain renderings
 * from other workflows. These renderings may all be bound to their own [ViewFactory]s. Regular
 * [ViewFactory]s and `LayoutRunner`s use
 * [WorkflowViewStub][com.squareup.workflow.ui.WorkflowViewStub] to recursively show nested
 * renderings using the [ViewRegistry][com.squareup.workflow.ui.ViewRegistry].
 *
 * View factories defined using this function may also show nested renderings. Doing so is as simple
 * as calling [ViewEnvironment.showRendering] and passing in the nested rendering. See the kdoc on
 * that function for an example.
 */
inline fun <reified RenderingT : Any> bindCompose(
  noinline showRendering: @Composable() (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ViewFactory<RenderingT> = ComposeViewFactory(RenderingT::class, showRendering)

@PublishedApi
internal class ComposeViewFactory<RenderingT : Any>(
  override val type: KClass<RenderingT>,
  internal val showRendering: @Composable() (RenderingT, ViewEnvironment) -> Unit
) : ViewFactory<RenderingT> {

  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    // There is currently no way to automatically generate an Android View directly from a
    // Composable function, so we need to use ViewGroup.setContent.
    val composeContainer = FrameLayout(contextForNewView)

    // Create a single MutableState to feed state updates into the composition.
    // We could also have two separate MutableStates, but using a Pair both makes it clear and
    // enforces that both values are always updated together.
    val renderState = mutableStateOf<Pair<RenderingT, ViewEnvironment>?>(
        // This will be updated immediately by bindShowRendering below.
        value = null,
        areEquivalent = StructurallyEqual
    )

    // Models will throw if their properties are accessed when there is no frame open. Currently,
    // that will be the case if the model is accessed before any other Compose infrastructure has
    // ran, i.e. if this view factory is the first compose code to run in the app.
    // I believe that eventually there will be a global frame that will make this unnecessary.
    FrameManager.ensureStarted()

    // Update the state whenever a new rendering is emitted.
    composeContainer.bindShowRendering(
        initialRendering,
        initialViewEnvironment
    ) { rendering, environment ->
      // This lambda will be executed synchronously before bindShowRendering returns.
      renderState.value = Pair(rendering, environment)
    }

    // Entry point to the world of Compose.
    composeContainer.setContent(Recomposer.current()) {
      val (rendering, environment) = renderState.value!!
      showRendering(rendering, environment)
    }

    return composeContainer
  }
}
