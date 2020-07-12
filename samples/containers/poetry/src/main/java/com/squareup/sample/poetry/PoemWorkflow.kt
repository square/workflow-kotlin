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
@file:Suppress("DEPRECATION", "OverridingDeprecatedMember")

package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemWorkflow.ClosePoem
import com.squareup.sample.poetry.StanzaWorkflow.Output.CloseStanzas
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowNextStanza
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowPreviousStanza
import com.squareup.sample.poetry.StanzaWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.ImplicitWorkflow
import com.squareup.workflow1.savedIntState
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.backstack.toBackStackScreen

/**
 * Renders a [Poem] as a [OverviewDetailScreen], whose overview is a [StanzaListRendering]
 * for the poem, and whose detail traverses through [StanzaRendering]s.
 */
// Compiler doesn't support operator assignment for delegated properties.
@Suppress("ReplaceWithOperatorAssignment")
object PoemWorkflow : ImplicitWorkflow<Poem, ClosePoem, OverviewDetailScreen>() {
  object ClosePoem

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun Ctx.render(): OverviewDetailScreen {
    var state by savedIntState { -1 }
    fun clearSelection() = update { state = -1 }
    fun selectPrevious() = update { state = state - 1 }
    fun selectNext() = update { state = state + 1 }
    fun handleStanzaListOutput(selection: Int) = update {
      if (selection == -1) setOutput(ClosePoem)
      state = selection
    }

    val previousStanzas: List<StanzaRendering> =
      if (state == -1) emptyList()
      else props.stanzas.subList(0, state)
          .mapIndexed { index, _ ->
            renderChild(StanzaWorkflow, Props(props, index), "$index") {}
          }

    val visibleStanza =
      if (state < 0) {
        null
      } else {
        renderChild(StanzaWorkflow, Props(props, state), "$state") {
          when (it) {
            CloseStanzas -> clearSelection()
            ShowPreviousStanza -> selectPrevious()
            ShowNextStanza -> selectNext()
          }
        }
      }

    val stackedStanzas = visibleStanza?.let {
      (previousStanzas + visibleStanza).toBackStackScreen<Any>()
    }

    val stanzaIndex =
      renderChild(StanzaListWorkflow, props) { selected ->
        handleStanzaListOutput(selected)
      }
          .copy(selection = state)
          .let { BackStackScreen<Any>(it) }

    return stackedStanzas
        ?.let { OverviewDetailScreen(overviewRendering = stanzaIndex, detailRendering = it) }
        ?: OverviewDetailScreen(
            overviewRendering = stanzaIndex,
            selectDefault = { handleStanzaListOutput(0) }
        )
  }
}
