package com.squareup.sample.poetry

import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

/**
 * Renders a given ordered list of [Poem]s. Reports the index of any that are clicked via Output.
 */
object PoemListWorkflow : StatelessWorkflow<List<Poem>, SelectedPoem, PoemListScreen>() {

  override fun render(
    renderProps: List<Poem>,
    context: RenderContext
  ): PoemListScreen {
    return PoemListScreen(
      poems = renderProps,
      onPoemSelected = context.eventHandler { index -> setOutput(index) }
    )
  }
}
