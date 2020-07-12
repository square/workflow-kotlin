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
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.Rendering
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Able
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Baker
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Charlie
import com.squareup.workflow1.ImplicitWorkflow
import com.squareup.workflow1.ui.ParcelableSaver
import kotlinx.android.parcel.Parcelize

object HelloBackButtonWorkflow : ImplicitWorkflow<Unit, Nothing, Rendering>() {
  @Parcelize
  enum class State : Parcelable {
    Able,
    Baker,
    Charlie;
  }

  data class Rendering(
    val message: String,
    val onClick: () -> Unit,
    val onBackPressed: (() -> Unit)?
  )

  override fun Ctx.render(): Rendering {
    var state by savedState(saver = ParcelableSaver()) { Able }

    fun advance() = update {
      state = when (state) {
        Able -> Baker
        Baker -> Charlie
        Charlie -> Able
      }
    }

    fun retreat() = update {
      state = when (state) {
        Able -> throw IllegalStateException()
        Baker -> Able
        Charlie -> Baker
      }
    }

    return Rendering(
        message = "$state",
        onClick = { advance() },
        onBackPressed = { retreat() }.takeIf { state != Able }
    )
  }
}
