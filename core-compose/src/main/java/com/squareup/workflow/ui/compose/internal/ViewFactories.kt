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
package com.squareup.workflow.ui.compose.internal

import android.view.View
import android.view.ViewGroup
import androidx.compose.Composable
import androidx.compose.compositionReference
import androidx.compose.onPreCommit
import androidx.compose.remember
import androidx.ui.core.AndroidOwner
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.OwnerAmbient
import androidx.ui.core.Ref
import androidx.ui.foundation.Box
import androidx.ui.viewinterop.AndroidView
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.canShowRendering
import com.squareup.workflow.ui.compose.ComposeViewFactory
import com.squareup.workflow.ui.showRendering

/**
 * Renders [rendering] into the composition using [viewFactory].
 *
 * To display a nested rendering from a
 * [Composable view binding][com.squareup.workflow.ui.compose.composedViewFactory], use the overload
 * without a [ViewFactory] parameter.
 *
 * *Note: [rendering] must be the same type as this [ViewFactory], even though the type system does
 * not enforce this constraint. This is due to a Compose compiler bug tracked
 * [here](https://issuetracker.google.com/issues/156527332).
 *
 * @see com.squareup.workflow.ui.compose.WorkflowRendering
 */
@Composable internal fun <RenderingT : Any> WorkflowRendering(
  rendering: RenderingT,
  viewFactory: ViewFactory<RenderingT>,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    // "Fast" path: If the child binding is also a Composable, we don't need to go through the
    // legacy view system and can just invoke the binding's composable function directly.
    if (viewFactory is ComposeViewFactory) {
      viewFactory.content(rendering, viewEnvironment)
      return@Box
    }

    // "Slow" path: Create a legacy Android View to show the rendering, like WorkflowViewStub.
    ViewFactoryAndroidView(viewFactory, rendering, viewEnvironment)
  }
}

/**
 * This is effectively the logic of [com.squareup.workflow.ui.WorkflowViewStub], but translated
 * into Compose idioms. This approach has a few advantages:
 *
 *  - Avoids extra custom views required to host `WorkflowViewStub` inside a Composition. Its trick
 *    of replacing itself in its parent doesn't play nice with Compose.
 *  - Allows us to pass the correct parent view for inflation (the root of the composition).
 *  - Avoids `WorkflowViewStub` having to do its own lookup to find the correct [ViewFactory], since
 *    we already have the correct one.
 *
 * Like `WorkflowViewStub`, this function uses the [viewFactory] to create and memoize a [View] to
 * display the [rendering], keeps it updated with the latest [rendering] and [viewEnvironment], and
 * adds it to the composition.
 *
 * This function also passes a [ParentComposition] down through the [ViewEnvironment] so that if the
 * child view further nests any `ComposableViewFactory`s, they will be correctly subcomposed.
 */
@Composable private fun <R : Any> ViewFactoryAndroidView(
  viewFactory: ViewFactory<R>,
  rendering: R,
  viewEnvironment: ViewEnvironment
) {
  val childView = remember { Ref<View>() }

  // Plumb the current composition through the ViewEnvironment so any nested composable factories
  // get access to any ambients currently in effect. See setOrSubcomposeContent().
  val parentComposition = remember { ParentComposition() }
  parentComposition.reference = compositionReference()
  val wrappedEnvironment = remember(viewEnvironment) {
    viewEnvironment + (ParentComposition to parentComposition)
  }

  // A view factory can decide to recreate its view at any time. This also covers the case where
  // the value of the viewFactory argument has changed, including to one with a different type.
  if (childView.value?.canShowRendering(rendering) != true) {
    // If we don't pass the parent Android View, the child will have the wrong LayoutParams.
    // OwnerAmbient is deprecated, but the only way to get the root view currently. I've filed
    // a feature request to expose this as first-class API, see
    // https://issuetracker.google.com/issues/156875705.
    @Suppress("DEPRECATION")
    val parentView = (OwnerAmbient.current as? AndroidOwner)?.view as? ViewGroup

    childView.value = viewFactory.buildView(
        initialRendering = rendering,
        initialViewEnvironment = wrappedEnvironment,
        contextForNewView = ContextAmbient.current,
        container = parentView
    )
  }

  // Invoke the ViewFactory's update logic whenever the view, the rendering, or the ViewEnvironment
  // change.
  onPreCommit(childView.value, rendering, wrappedEnvironment) {
    childView.value!!.showRendering(rendering, wrappedEnvironment)
  }

  AndroidView(childView.value!!)
}
