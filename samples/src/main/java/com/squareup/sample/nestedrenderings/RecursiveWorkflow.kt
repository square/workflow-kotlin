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
package com.squareup.sample.nestedrenderings

import com.squareup.sample.nestedrenderings.RecursiveWorkflow.LegacyRendering
import com.squareup.sample.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.sample.nestedrenderings.RecursiveWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild

/**
 * A simple workflow that produces [Rendering]s of zero or more children.
 * The rendering provides event handlers for adding children and resetting child count to zero.
 *
 * Every other (odd) rendering in the [Rendering.children] will be wrapped with a [LegacyRendering]
 * to force it to go through the legacy view layer. This way this sample both demonstrates pass-
 * through Composable renderings as well as adapting in both directions.
 */
object RecursiveWorkflow : StatefulWorkflow<Unit, State, Nothing, Any>() {

  data class State(val children: Int = 0)

  /**
   * A rendering from a [RecursiveWorkflow].
   *
   * @param children A list of renderings to display as children of this rendering.
   * @param onAddChildClicked Adds a child to [children].
   * @param onResetClicked Resets [children] to an empty list.
   */
  data class Rendering(
    val children: List<Any>,
    val onAddChildClicked: () -> Unit,
    val onResetClicked: () -> Unit
  )

  /**
   * Wrapper around a [Rendering] that will be implemented using a legacy view.
   */
  data class LegacyRendering(val rendering: Any)

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State()

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): Rendering {
    return Rendering(
        children = List(state.children) { i ->
          val child = context.renderChild(RecursiveWorkflow, key = i.toString())
          if (i % 2 == 0) child else LegacyRendering(child)
        },
        onAddChildClicked = { context.actionSink.send(addChild()) },
        onResetClicked = { context.actionSink.send(reset()) }
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun addChild() = action {
    state = state.copy(children = state.children + 1)
  }

  private fun reset() = action {
    state = State()
  }
}
