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
package com.squareup.sample.helloworkflow

import com.squareup.sample.helloworkflow.HelloWorkflow.Rendering
import com.squareup.sample.helloworkflow.HelloWorkflow.State.Goodbye
import com.squareup.sample.helloworkflow.HelloWorkflow.State.Hello
import com.squareup.workflow1.ImplicitWorkflow
import com.squareup.workflow1.savedEnumState

object HelloWorkflow : ImplicitWorkflow<Unit, Nothing, Rendering>() {
  enum class State {
    Hello,
    Goodbye
  }

  data class Rendering(
    val message: String,
    val onClick: () -> Unit
  )

  override fun Ctx.render(): Rendering {
    var state by savedEnumState { Hello }
    fun helloAction() = update {
      state = when (state) {
        Hello -> Goodbye
        Goodbye -> Hello
      }
    }

    return Rendering(
        message = state.name,
        onClick = { helloAction() }
    )
  }
}
