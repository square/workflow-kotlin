package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemListScreen.Companion.NO_POEM_SELECTED
import com.squareup.sample.poetry.PoemListWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen

typealias SelectedPoem = Int

/**
 * Default implementation of [PoemsBrowserWorkflow]. Note the use of
 * a child [PoemWorkflow] to render the selected [Poem], and in particular
 * how the child's overview pane (the list of stanzas) is combined with
 * the list of poems, giving us back stack navigation on both sides of
 * the splitter.
 */
class RealPoemsBrowserWorkflow(
  private val poemWorkflow: PoemWorkflow
) : PoemsBrowserWorkflow,
  StatefulWorkflow<List<Poem>, SelectedPoem, Unit, OverviewDetailScreen>() {

  override fun initialState(
    props: List<Poem>,
    snapshot: Snapshot?
  ): SelectedPoem {
    return snapshot?.bytes?.parse { source ->
      source.readInt()
    } ?: NO_POEM_SELECTED
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: List<Poem>,
    renderState: SelectedPoem,
    context: RenderContext
  ): OverviewDetailScreen {
    val poems: OverviewDetailScreen =
      context.renderChild(PoemListWorkflow, Props(poems = renderProps)) { selected ->
        choosePoem(
          selected
        )
      }
        .copy(selection = renderState)
        .let { OverviewDetailScreen(BackStackScreen(it)) }

    return if (renderState == NO_POEM_SELECTED) {
      poems
    } else {
      val poem: OverviewDetailScreen =
        context.renderChild(poemWorkflow, renderProps[renderState]) { clearSelection }
      poems + poem
    }
  }

  override fun snapshotState(state: SelectedPoem): Snapshot =
    Snapshot.write { sink -> sink.writeInt(state) }

  private fun choosePoem(
    index: Int
  ) = action("goToPoem") {
    state = index
  }

  private val clearSelection = choosePoem(NO_POEM_SELECTED)
}
