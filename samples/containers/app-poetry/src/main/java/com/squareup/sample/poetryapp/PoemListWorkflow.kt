package com.squareup.sample.poetryapp

import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

/**
 * Renders a given ordered list of [Poem]s. Reports the index of any that are clicked.
 */
object PoemListWorkflow : StatelessWorkflow<List<Poem>, Int, PoemListRendering>() {

  override fun render(
    props: List<Poem>,
    context: RenderContext
  ): PoemListRendering {
    return PoemListRendering(
        poems = props,
        onPoemSelected = context.eventHandler { index -> setOutput(index) }
    )
  }
}

data class PoemListRendering(
  val poems: List<Poem>,
  val onPoemSelected: (Int) -> Unit,
  val selection: Int = -1
)
