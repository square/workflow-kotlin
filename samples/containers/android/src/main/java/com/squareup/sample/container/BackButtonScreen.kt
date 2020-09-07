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
package com.squareup.sample.container

import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.buildView
import com.squareup.workflow1.ui.getShowRendering

/**
 * Adds optional back button handling to a [wrapped] rendering, possibly overriding that
 * the wrapped rendering's own back button handler.
 *
 * @param override If `true`, [onBackPressed] is set as the
 * [backPressedHandler][android.view.View.backPressedHandler] after
 * the [wrapped] rendering's view is built / updated. If false, ours
 * is set afterward, to allow the wrapped rendering to take precedence.
 * Defaults to `false`.
 *
 * @param onBackPressed The function to fire when the device back button
 * is pressed, or null to set no handler. Defaults to `null`.
 */
@WorkflowUiExperimentalApi
data class BackButtonScreen<W : Any>(
  val wrapped: W,
  val override: Boolean = false,
  val onBackPressed: (() -> Unit)? = null
) {
  companion object : ViewFactory<BackButtonScreen<*>>
  by BuilderViewFactory(
      type = BackButtonScreen::class,
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
              val wrappedUpdater = view.getShowRendering<Any>()!!

              view.bindShowRendering(initialRendering, initialEnv) { rendering, environment ->
                if (!rendering.override) {
                  // Place our handler before invoking the wrapped updater, so that
                  // its later calls to view.backPressedHandler will take precedence
                  // over ours.
                  view.backPressedHandler = rendering.onBackPressed
                }

                wrappedUpdater.invoke(rendering.wrapped, environment)

                if (rendering.override) {
                  // Place our handler after invoking the wrapped updater, so that ours
                  // wins.
                  view.backPressedHandler = rendering.onBackPressed
                }
              }
            }
      }
  )
}
