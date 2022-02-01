package com.squareup.sample.poetry

import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

/**
 * Given a [Poem], renders a list of its [initialStanzas][Poem.initialStanzas].
 *
 * Output is the index of a clicked stanza, or -1 on exit.
 */
object StanzaListWorkflow : StatelessWorkflow<Poem, Int, StanzaListScreen>() {

  override fun render(
    renderProps: Poem,
    context: RenderContext
  ): StanzaListScreen {
    return StanzaListScreen(
      title = renderProps.title,
      subtitle = renderProps.poet.fullName,
      firstLines = renderProps.initialStanzas,
      onStanzaSelected = context.eventHandler { index -> setOutput(index) },
      onExit = context.eventHandler { setOutput(-1) }
    )
  }
}
