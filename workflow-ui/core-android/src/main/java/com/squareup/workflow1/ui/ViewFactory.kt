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

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Factory for [View]s that can show [ViewRendering]s of a particular [type][RenderingT].
 *
 * Use [LayoutRunner.bind] to work with XML layout resources and
 * [AndroidX ViewBinding][androidx.viewbinding.ViewBinding], or [BuilderViewFactory] to
 * create views from code.
 *
 * Sets of bindings are gathered in [ViewRegistry] instances.
 */
@WorkflowUiExperimentalApi
interface ViewFactory<RenderingT : ViewRendering> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a [View] to display [initialRendering]. This method must call [View.bindShowRendering]
   * on the new View to display [initialRendering], and to make the View ready to respond
   * to succeeding calls to [View.showRendering].
   */
  fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View
}

@WorkflowUiExperimentalApi
@Suppress("unused")
@Deprecated(
    "Use ViewFactory.",
    ReplaceWith("ViewFactory<RenderingT>", "com.squareup.workflow1.ui.ViewFactory")
)
typealias ViewBinding<RenderingT> = ViewFactory<RenderingT>
