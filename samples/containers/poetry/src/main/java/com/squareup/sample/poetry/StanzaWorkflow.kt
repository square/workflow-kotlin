package com.squareup.sample.poetry

import com.squareup.sample.poetry.StanzaWorkflow.Output
import com.squareup.sample.poetry.StanzaWorkflow.Output.CloseStanzas
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowNextStanza
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowPreviousStanza
import com.squareup.sample.poetry.StanzaWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

object StanzaWorkflow : StatelessWorkflow<Props, Output, StanzaRendering>() {
  data class Props(
    val poem: Poem,
    val index: Int
  )

  enum class Output {
    CloseStanzas,
    ShowPreviousStanza,
    ShowNextStanza
  }

  override fun render(
    props: Props,
    context: RenderContext
  ): StanzaRendering {
    with(props) {
      val onGoBack: (() -> Unit)? = when (index) {
        0 -> null
        else -> {
          context.eventHandler { setOutput(ShowPreviousStanza) }
        }
      }

      val onGoForth: (() -> Unit)? = when (index) {
        poem.stanzas.size - 1 -> null
        else -> {
          context.eventHandler { setOutput(ShowNextStanza) }
        }
      }

      return StanzaRendering(
          onGoUp = context.eventHandler { setOutput(CloseStanzas) },
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
