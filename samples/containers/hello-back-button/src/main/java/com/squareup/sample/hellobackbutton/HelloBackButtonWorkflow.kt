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
package com.squareup.sample.hellobackbutton

import android.os.Parcelable
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Able
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Baker
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Charlie
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.ViewRendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.toParcelable
import com.squareup.workflow1.ui.toSnapshot
import kotlinx.android.parcel.Parcelize

@OptIn(WorkflowUiExperimentalApi::class)
object HelloBackButtonWorkflow : StatefulWorkflow<Unit, State, Nothing, ViewRendering>() {
  @Parcelize
  enum class State : Parcelable {
    Able,
    Baker,
    Charlie;
  }

  internal data class Rendering(
    val message: String,
    val onClick: () -> Unit,
    val onBackPressed: (() -> Unit)?
  ) : ViewRendering

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.toParcelable() ?: Able

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): ViewRendering {
    return Rendering(
        message = "$state",
        onClick = { context.actionSink.send(advance) },
        onBackPressed = { context.actionSink.send(retreat) }.takeIf { state != Able }
    )
  }

  override fun snapshotState(state: State): Snapshot = state.toSnapshot()

  private val advance = action {
    state = when (state) {
      Able -> Baker
      Baker -> Charlie
      Charlie -> Able
    }
  }

  private val retreat = action {
    state = when (state) {
      Able -> throw IllegalStateException()
      Baker -> Able
      Charlie -> Baker
    }
  }
}
