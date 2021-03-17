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
package com.squareup.workflow1.ui.compose.internal

import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * Applies [transform] to each [ViewFactory] in this registry. Transformations are applied lazily,
 * at the time of lookup via [ViewRegistry.getFactoryFor].
 */
@WorkflowUiExperimentalApi
internal fun ViewRegistry.mapFactories(
  transform: (ViewFactory<*>) -> ViewFactory<*>
): ViewRegistry = object : ViewRegistry {
  override val keys: Set<KClass<*>> get() = this@mapFactories.keys

  override fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT> {
    val factoryFor =
      this@mapFactories.getFactoryFor(renderingType) ?: throw IllegalArgumentException(
          "A ${ViewFactory::class.qualifiedName} should have been registered to display " +
              "${renderingType.qualifiedName} instances, or that class should implement " +
              "${AndroidViewRendering::class.simpleName}<${renderingType.simpleName}>."
      )
    val transformedFactory = transform(factoryFor)
    check(transformedFactory.type == renderingType) {
      "Expected transform to return a ViewFactory that is compatible with $renderingType, " +
          "but got one with type ${transformedFactory.type}"
    }
    @Suppress("UNCHECKED_CAST")
    return transformedFactory as ViewFactory<RenderingT>
  }
}
