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

package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.platform.ComposeView
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering
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
 * val FooBinding = composedViewFactory { showFoo(it) }
 *
 * @Composable
 * private fun showFoo(foo: FooRendering) {
 *   Text(foo.message)
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
 * [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub] to recursively show nested
 * renderings using the [ViewRegistry][com.squareup.workflow1.ui.ViewRegistry].
 *
 * View factories defined using this function may also show nested renderings. Doing so is as simple
 * as calling [WorkflowRendering] and passing in the nested rendering. See the kdoc on that function
 * for an example.
 *
 * Nested renderings will have access to any ambients defined in outer composable, even if there are
 * legacy views in between them, as long as the [ViewEnvironment] is propagated continuously between
 * the two factories.
 *
 * ## Initializing Compose context
 *
 * Often all the [composedViewFactory] factories in an app need to share some context – for example,
 * certain ambients need to be provided, such as `MaterialTheme`. To configure this shared context,
 * call [withCompositionRoot] on your top-level [ViewEnvironment]. The first time a
 * [composedViewFactory] is used to show a rendering, its [showRendering] function will be wrapped
 * with the [CompositionRoot]. See the documentation on [CompositionRoot] for more information.
 */
@WorkflowUiExperimentalApi
inline fun <reified RenderingT : Any> composedViewFactory(
  noinline showRendering: @Composable (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ViewFactory<RenderingT> = ComposeViewFactory(RenderingT::class, showRendering)

@WorkflowUiExperimentalApi
@PublishedApi
internal class ComposeViewFactory<RenderingT : Any>(
  override val type: KClass<RenderingT>,
  internal val content: @Composable (RenderingT, ViewEnvironment) -> Unit
) : ViewFactory<RenderingT> {

  @OptIn(ExperimentalComposeApi::class)
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    val composeContainer = ComposeView(contextForNewView)

    // Update the state whenever a new rendering is emitted.
    // This lambda will be executed synchronously before bindShowRendering returns.
    composeContainer.bindShowRendering(
        initialRendering,
        initialViewEnvironment
    ) { rendering, environment ->
      // Entry point to the world of Compose.
      composeContainer.setContent {
        content(rendering, environment)
      }
    }

    return composeContainer
  }
}
