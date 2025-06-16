package com.squareup.sample.poetry

import com.squareup.sample.poetry.StanzaWorkflow.Output
import com.squareup.sample.poetry.StanzaWorkflow.Output.CloseStanzas
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowNextStanza
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowPreviousStanza
import com.squareup.sample.poetry.StanzaWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

object StanzaWorkflow : StatelessWorkflow<Props, Output, StanzaScreen>() {
  data class Props(
    val poem: Poem,
    val index: Int,
    val eventHandlerTag: (String) -> String = { it }
  )

  enum class Output {
    CloseStanzas,
    ShowPreviousStanza,
    ShowNextStanza
  }

  override fun render(
    renderProps: Props,
    context: RenderContext<Props, Output>
  ): StanzaScreen {
    with(renderProps) {
      val onGoBack: (() -> Unit)? = when (index) {
        0 -> null
        else -> {
          context.eventHandler(
            name = renderProps.eventHandlerTag("E-StanzaWorkflow-${ShowPreviousStanza.name}")
          ) {
            setOutput(
              ShowPreviousStanza
            )
          }
        }
      }

      val onGoForth: (() -> Unit)? = when (index) {
        poem.stanzas.size - 1 -> null
        else -> {
          context.eventHandler(
            name = renderProps.eventHandlerTag("E-StanzaWorkflow-${ShowNextStanza.name}")
          ) {
            setOutput(
              ShowNextStanza
            )
          }
        }
      }

      return StanzaScreen(
        onGoUp = context.eventHandler(
          name = renderProps.eventHandlerTag("E-StanzaWorkflow-${CloseStanzas.name}")
        ) {
          setOutput(
            CloseStanzas
          )
        },
        title = poem.title,
        stanzaNumber = index + 1,
        lines = poem.stanzas[index],
        onGoBack = onGoBack,
        onGoForth = onGoForth
      )
    }
  }
}
