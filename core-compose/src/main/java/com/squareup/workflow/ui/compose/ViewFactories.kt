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
package com.squareup.workflow.ui.compose

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.WorkflowViewStub
import com.squareup.workflow.ui.compose.ComposableViewStubWrapper.Update

/**
 * Renders [rendering] into the composition using the `ViewRegistry` from the [ViewEnvironment] to
 * determine how to draw it.
 *
 * To display a nested rendering from a [Composable view binding][bindCompose], use
 * [ViewEnvironment.showRendering].
 *
 * *Note: [rendering] must be the same type as this [ViewFactory], even though the type system does
 * not enforce this constraint. This is due to a Compose compiler bug tracked
 * [here](https://issuetracker.google.com/issues/156527332).*
 *
 * @see ViewEnvironment.showRendering
 * @see com.squareup.workflow.ui.ViewRegistry.showRendering
 */
// TODO(https://issuetracker.google.com/issues/156527332) Should be ViewFactory<RenderingT>
@Composable internal fun <RenderingT : Any> ViewFactory<Any>.showRendering(
  rendering: RenderingT,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    // Fast path: If the child binding is also a Composable, we don't need to go through the legacy
    // view system and can just invoke the binding's composable function directly.
    if (this is ComposeViewFactory) {
      showRendering(rendering, viewEnvironment)
    } else {
      // Plumb the current composition "context" through the ViewEnvironment so any nested composable
      // factories get access to any ambients currently in effect.
      // See setOrContinueContent().
      val newEnvironment = viewEnvironment.withCompositionContinuation()

      // IntelliJ currently complains very loudly about this function call, but it actually compiles.
      // The IDE tooling isn't currently able to recognize that the Compose compiler accepts this code.
      ComposableViewStubWrapper(update = Update(rendering, newEnvironment))
    }
  }
}

/**
 * Wraps a [WorkflowViewStub] with an API that is more Compose-friendly.
 *
 * In particular, Compose will only generate `Emittable`s for views with a single constructor
 * that takes a [Context].
 *
 * See [this slack message](https://kotlinlang.slack.com/archives/CJLTWPH7S/p1576264533012000?thread_ts=1576262311.008800&cid=CJLTWPH7S).
 */
private class ComposableViewStubWrapper(context: Context) : FrameLayout(context) {

  data class Update(
    val rendering: Any,
    val viewEnvironment: ViewEnvironment
  )

  private val viewStub = WorkflowViewStub(context)

  init {
    addView(viewStub)
  }

  // Compose turns this into a parameter when you invoke this class as a Composable.
  fun setUpdate(update: Update) {
    viewStub.update(update.rendering, update.viewEnvironment)
  }

  override fun getLayoutParams(): LayoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
}
