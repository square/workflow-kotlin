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

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Ref
import androidx.compose.ui.viewinterop.AndroidView
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.canShowRendering
import com.squareup.workflow.ui.compose.ComposeViewFactory
import com.squareup.workflow.ui.compose.WorkflowContainer
import com.squareup.workflow1.ui.getRendering
import com.squareup.workflow1.ui.showRendering
import kotlin.properties.Delegates.observable

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
@WorkflowUiExperimentalApi
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
 * This is effectively the logic of [com.squareup.workflow1.ui.WorkflowViewStub], but translated
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
 */
@WorkflowUiExperimentalApi
@Composable private fun <R : Any> ViewFactoryAndroidView(
  viewFactory: ViewFactory<R>,
  rendering: R,
  viewEnvironment: ViewEnvironment
) {
  // We can't trigger subcompositions during the composition itself, we have to wait until
  // the composition is committed. So instead of sending the update in the AndroidView update
  // lambda, we just store the view here, and then send the update and view factory in an
  // onPreCommit hook. See https://github.com/square/workflow-kotlin-compose/issues/67.
  val hostViewRef = remember { Ref<HostView>() }

  AndroidView(::HostView) {
    hostViewRef.value = it
  }

  SideEffect {
    hostViewRef.value?.let { hostView ->
      hostView.viewFactory = viewFactory
      hostView.update = Pair(rendering, viewEnvironment)
    }
  }
}

/**
 * This is basically a clone of WorkflowViewStub, but it takes an explicit [ViewFactory] instead
 * of looking one up itself, and doesn't do the replace-in-parent trick.
 *
 * It doesn't seem possible to create the view inside a Composable directly and use
 * [AndroidView]. I can't figure out exactly why it doesn't work, but I
 * think it has something to do with getting into an incorrect state if a non-Composable view
 * factory synchronously builds and binds a ComposableViewFactory in buildView. In that case, the
 * second and subsequent compose passes will lose ambients from the parent composition. I've spent
 * a bunch of time trying to debug compose internals and trying different approaches to figure out
 * why that is, but nothing makes sense. All I know is that using a custom view like this seems to
 * fix it.
 *
 * …Except in the case where the highest-level ComposableViewFactory isn't a subcomposition (i.e.
 * the workflow is being ran with a [WorkflowLayout] instead of [WorkflowContainer]). Or maybe it's
 * only if the top-level ViewFactory is such a ComposableViewFactory, I haven't tested other legacy
 * view factories between the root and the top-level CVF. In that case, there seems to be a race
 * condition with measuring and second compose pass will throw an exception about an unmeasured
 * node.
 */
@WorkflowUiExperimentalApi
private class HostView(context: Context) : FrameLayout(context) {

  private var rerender = true
  private var view: View? = null

  var viewFactory by observable<ViewFactory<*>?>(null) { _, old, new ->
    if (old != new) {
      update()
    }
  }

  var update by observable<Pair<Any, ViewEnvironment>?>(null) { _, old, new ->
    if (old != new) {
      rerender = true
      update()
    }
  }

  init {
    layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
  }

  private fun update() {
    if (viewFactory == null) return
    val (rendering, viewEnvironment) = update ?: return

    if (view?.canShowRendering(rendering) != true) {
      // BuildView must call bindShowRendering, which will call showRendering.
      @Suppress("UNCHECKED_CAST")
      view = (viewFactory as ViewFactory<Any>)
        .buildView(rendering, viewEnvironment, context, this)

      check(view!!.getRendering<Any>() != null) {
        "View.bindShowRendering should have been called for $this, typically by the " +
            "${ViewFactory::class.java.name} that created it."
      }
      removeAllViews()
      addView(view)
    } else if (rerender) {
      view!!.showRendering(rendering, viewEnvironment)
    }

    rerender = false
  }
}
