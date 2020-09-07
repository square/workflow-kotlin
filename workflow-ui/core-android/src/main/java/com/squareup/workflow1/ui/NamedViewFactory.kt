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
package com.squareup.workflow1.ui

/**
 * [ViewFactory] that allows views to display instances of [Named]. Delegates
 * to the factory for [Named.wrapped].
 */
@WorkflowUiExperimentalApi
object NamedViewFactory : ViewFactory<Named<*>>
by BuilderViewFactory(
    type = Named::class,
    viewConstructor = { initialRendering, initialEnv, contextForNewView, container ->
      // Have the ViewRegistry build the view for wrapped.
      initialEnv[ViewRegistry]
          .buildView(
              initialRendering.wrapped,
              initialEnv,
              contextForNewView,
              container
          )
          .also { view ->
            // Rendering updates will be instances of Named, but the view
            // was built to accept updates matching the type of wrapped.
            // So replace the view's update function with one of our
            // own, which calls through to the original.

            val wrappedUpdater = view.getShowRendering<Any>()!!

            view.bindShowRendering(initialRendering, initialEnv) { rendering, environment ->
              wrappedUpdater.invoke(rendering.wrapped, environment)
            }
          }
    }
)
