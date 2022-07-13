package com.squareup.sample.poetry

import com.squareup.sample.poetry.PoemListWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.StatelessWorkflow

/**
 * Renders a given ordered list of [Poem]s. Reports the index of any that are clicked via Output.
 */
object PoemListWorkflow : StatelessWorkflow<Props, Int, PoemListScreen>() {

  data class Props(
    val poems: List<Poem>,
    val eventHandlerTag: (String) -> String = { "" }
  )

  override fun render(
    renderProps: Props,
    context: RenderContext
  ): PoemListScreen {
    return PoemListScreen(
      poems = renderProps.poems,
      onPoemSelected = context.eventHandler(
        name = { renderProps.eventHandlerTag("E-PoemList-PoemSelected") }
      ) { index -> setOutput(index) }
    )
  }
}
