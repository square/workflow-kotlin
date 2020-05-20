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
package com.squareup.workflow.ui.compose.tooling

import androidx.compose.Composable
import androidx.compose.Immutable
import androidx.compose.remember
import androidx.ui.core.Modifier
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.ViewRegistry
import kotlin.reflect.KClass

/**
 * Creates and [remember]s a [ViewEnvironment] that has a special [ViewRegistry] and any additional
 * elements as configured by [viewEnvironmentUpdater].
 *
 * The [ViewRegistry] will contain [mainFactory] if specified, as well as a [placeholderViewFactory]
 * that will be used to show any renderings that don't match [mainFactory]'s type. All placeholders
 * will have [placeholderModifier] applied.
 */
@Composable internal fun previewViewEnvironment(
  placeholderModifier: Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null,
  mainFactory: ViewFactory<*>? = null
): ViewEnvironment {
  val viewRegistry = remember(mainFactory, placeholderModifier) {
    PreviewViewRegistry(mainFactory, placeholderViewFactory(placeholderModifier))
  }
  return remember(viewRegistry, viewEnvironmentUpdater) {
    ViewEnvironment(viewRegistry).let { environment ->
      // Give the preview a chance to add its own elements to the ViewEnvironment.
      viewEnvironmentUpdater?.let { it(environment) } ?: environment
    }
  }
}

/**
 * A [ViewRegistry] that uses [mainFactory] for rendering [RenderingT]s, and [placeholderFactory]
 * for all other [WorkflowRendering][com.squareup.workflow.ui.compose.WorkflowRendering] calls.
 */
@Immutable
private class PreviewViewRegistry<RenderingT : Any>(
  private val mainFactory: ViewFactory<RenderingT>? = null,
  private val placeholderFactory: ViewFactory<Any>
) : ViewRegistry {
  override val keys: Set<KClass<*>> get() = mainFactory?.let { setOf(it.type) } ?: emptySet()

  @Suppress("UNCHECKED_CAST")
  override fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT> = when (renderingType) {
    mainFactory?.type -> mainFactory
    else -> placeholderFactory
  } as ViewFactory<RenderingT>
}
