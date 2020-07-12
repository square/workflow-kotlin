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
package com.squareup.sample.poetry

import com.squareup.sample.poetry.StanzaWorkflow.Output
import com.squareup.sample.poetry.StanzaWorkflow.Output.CloseStanzas
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowNextStanza
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowPreviousStanza
import com.squareup.sample.poetry.StanzaWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.ImplicitWorkflow
import com.squareup.workflow1.Sink
import com.squareup.workflow1.makeEventSink
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

object StanzaWorkflow : ImplicitWorkflow<Props, Output, StanzaRendering>() {
  data class Props(
    val poem: Poem,
    val index: Int
  )

  enum class Output {
    CloseStanzas,
    ShowPreviousStanza,
    ShowNextStanza
  }

  override fun Ctx.render(): StanzaRendering {
    with(props) {
      val sink: Sink<Output> = makeEventSink { setOutput(it) }

      val onGoBack: (() -> Unit)? = when (index) {
        0 -> null
        else -> {
          { sink.send(ShowPreviousStanza) }
        }
      }

      val onGoForth: (() -> Unit)? = when (index) {
        poem.stanzas.size - 1 -> null
        else -> {
          { sink.send(ShowNextStanza) }
        }
      }

      return StanzaRendering(
          onGoUp = { sink.send(CloseStanzas) },
          title = poem.title,
          stanzaNumber = index + 1,
          lines = poem.stanzas[index],
          onGoBack = onGoBack,
          onGoForth = onGoForth
      )
    }
  }
}

@OptIn(WorkflowUiExperimentalApi::class)
data class StanzaRendering(
  val title: String,
  val stanzaNumber: Int,
  val lines: List<String>,
  val onGoUp: () -> Unit,
  val onGoBack: (() -> Unit)? = null,
  val onGoForth: (() -> Unit)? = null
) : Compatible {
  override val compatibilityKey = "$title: $stanzaNumber"
}
