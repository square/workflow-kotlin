package com.squareup.sample.poetry

import com.squareup.sample.poetry.PoemWorkflow.NO_SELECTED_STANZA
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

/**
 * Given a [Poem], renders a list of its [initialStanzas][Poem.initialStanzas].
 *
 * Output is the index of a clicked stanza, or [-1][NO_SELECTED_STANZA] on exit.
 */
object StanzaListWorkflow : StatelessWorkflow<Poem, SelectedStanza, StanzaListRendering>() {

  override fun render(
    renderProps: Poem,
    context: RenderContext
  ): StanzaListRendering {
    return StanzaListRendering(
      title = renderProps.title,
      subtitle = renderProps.poet.fullName,
      firstLines = renderProps.initialStanzas,
      onStanzaSelected = context.eventHandler { index -> setOutput(index) },
      onExit = context.eventHandler { setOutput(NO_SELECTED_STANZA) }
    )
  }
}
