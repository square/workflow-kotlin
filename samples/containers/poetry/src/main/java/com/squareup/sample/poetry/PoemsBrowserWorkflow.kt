package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemListRendering.Companion.NO_POEM_SELECTED
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen

typealias SelectedPoem = Int

object PoemsBrowserWorkflow :
  StatefulWorkflow<List<Poem>, SelectedPoem, Nothing, OverviewDetailScreen>() {

  override fun initialState(
    props: List<Poem>,
    snapshot: Snapshot?
  ): SelectedPoem {
    return snapshot?.bytes?.parse { source -> source.readInt() }
      ?: NO_POEM_SELECTED
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: List<Poem>,
    renderState: SelectedPoem,
    context: RenderContext
  ): OverviewDetailScreen {
    val poems: OverviewDetailScreen =
      context.renderChild(PoemListWorkflow, renderProps) { selected -> choosePoem(selected) }
        .copy(selection = renderState)
        .let { OverviewDetailScreen(BackStackScreen(it)) }

    return if (renderState == NO_POEM_SELECTED) {
      poems
    } else {
      val poem: OverviewDetailScreen =
        context.renderChild(PoemWorkflow, renderProps[renderState]) { clearSelection }
      poems + poem
    }
  }

  override fun snapshotState(state: SelectedPoem): Snapshot = Snapshot.write { sink ->
    sink.writeInt(state)
  }

  private fun choosePoem(index: SelectedPoem) = action("goToPoem") {
    state = index
  }

  private val clearSelection = choosePoem(NO_POEM_SELECTED)
}
