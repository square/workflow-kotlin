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
package com.squareup.sample.poetryapp

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemWorkflow
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.ImplicitWorkflow
import com.squareup.workflow1.savedIntState
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen

object PoemsBrowserWorkflow : ImplicitWorkflow<List<Poem>, Nothing, OverviewDetailScreen>() {

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun Ctx.render(): OverviewDetailScreen {
    var selectedPoem by savedIntState { -1 }
    fun choosePoem(index: Int) = update { selectedPoem = index }
    fun clearSelection() = choosePoem(-1)

    val poems: OverviewDetailScreen =
      renderChild(PoemListWorkflow, props) { selected -> choosePoem(selected) }
          .copy(selection = selectedPoem)
          .let { OverviewDetailScreen(BackStackScreen(it)) }

    return if (selectedPoem == -1) {
      poems
    } else {
      val poem: OverviewDetailScreen =
        renderChild(PoemWorkflow, props[selectedPoem]) { clearSelection() }
      poems + poem
    }
  }
}
