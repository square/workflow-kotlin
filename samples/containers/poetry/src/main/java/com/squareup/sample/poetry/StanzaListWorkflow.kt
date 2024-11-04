package com.squareup.sample.poetry

import com.squareup.sample.poetry.StanzaListWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

typealias SelectedStanza = Int

/**
 * Given a [Poem], renders a list of its [initialStanzas][Poem.initialStanzas].
 *
 * Output is the index of a clicked stanza, or [-1][NO_SELECTED_STANZA] on exit.
 */
object StanzaListWorkflow : StatelessWorkflow<Props, SelectedStanza, StanzaListScreen>() {

  data class Props(
    val poem: Poem,
    val eventHandlerTag: (String) -> String = { "" }
  )

  const val NO_SELECTED_STANZA = -1

  override fun render(
    renderProps: Props,
    context: RenderContext
  ): StanzaListScreen {
    val poem = renderProps.poem
    return StanzaListScreen(
      title = poem.title,
      subtitle = poem.poet.fullName,
      firstLines = poem.initialStanzas,
      onStanzaSelected = context.eventHandler(
        name = renderProps.eventHandlerTag("E-StanzaList-StanzaSelected")
      ) { index ->
        setOutput(
          index
        )
      },
      onExit = context.eventHandler(name = renderProps.eventHandlerTag("E-StanzaList-Exit")) {
        setOutput(
          NO_SELECTED_STANZA
        )
      }
    )
  }
}
