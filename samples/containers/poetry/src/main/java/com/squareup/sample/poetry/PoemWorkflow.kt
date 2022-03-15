package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemWorkflow.Action.ClearSelection
import com.squareup.sample.poetry.PoemWorkflow.Action.HandleStanzaListOutput
import com.squareup.sample.poetry.PoemWorkflow.Action.SelectNext
import com.squareup.sample.poetry.PoemWorkflow.Action.SelectPrevious
import com.squareup.sample.poetry.PoemWorkflow.ClosePoem
import com.squareup.sample.poetry.StanzaWorkflow.Output.CloseStanzas
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowNextStanza
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowPreviousStanza
import com.squareup.sample.poetry.StanzaWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.backstack.toBackStackScreen

typealias SelectedStanza = Int

/**
 * Renders a [Poem] as a [OverviewDetailScreen], whose overview is a [StanzaListRendering]
 * for the poem, and whose detail traverses through [StanzaRendering]s.
 */
object PoemWorkflow : StatefulWorkflow<Poem, SelectedStanza, ClosePoem, OverviewDetailScreen>() {
  object ClosePoem
  public const val NO_SELECTED_STANZA = -1

  override fun initialState(
    props: Poem,
    snapshot: Snapshot?
  ): Int {
    return snapshot?.bytes?.parse { source -> source.readInt() }
      ?: NO_SELECTED_STANZA
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: Poem,
    renderState: SelectedStanza,
    context: RenderContext
  ): OverviewDetailScreen {
    val previousStanzas: List<StanzaRendering> =
      if (renderState == NO_SELECTED_STANZA) emptyList()
      else renderProps.stanzas.subList(0, renderState)
        .mapIndexed { index, _ ->
          context.renderChild(StanzaWorkflow, Props(renderProps, index), "$index") {
            noAction()
          }
        }

    val visibleStanza =
      if (renderState < 0) {
        null
      } else {
        context.renderChild(
          StanzaWorkflow, Props(renderProps, renderState), "$renderState"
        ) {
          when (it) {
            CloseStanzas -> ClearSelection
            ShowPreviousStanza -> SelectPrevious
            ShowNextStanza -> SelectNext
          }
        }
      }

    val stackedStanzas = visibleStanza?.let {
      (previousStanzas + visibleStanza).toBackStackScreen<Any>()
    }

    val stanzaIndex =
      context.renderChild(StanzaListWorkflow, renderProps) { selected ->
        HandleStanzaListOutput(selected)
      }
        .copy(selection = renderState)
        .let { BackStackScreen<Any>(it) }

    return stackedStanzas
      ?.let { OverviewDetailScreen(overviewRendering = stanzaIndex, detailRendering = it) }
      ?: OverviewDetailScreen(
        overviewRendering = stanzaIndex,
        selectDefault = { context.actionSink.send(HandleStanzaListOutput(0)) }
      )
  }

  override fun snapshotState(state: Int): Snapshot = Snapshot.write { sink ->
    sink.writeInt(state)
  }

  private sealed class Action : WorkflowAction<Poem, SelectedStanza, ClosePoem>() {
    object ClearSelection : Action()
    object SelectPrevious : Action()
    object SelectNext : Action()
    class HandleStanzaListOutput(val selection: Int) : Action()
    object ExitPoem : Action()

    override fun Updater.apply() {
      when (this@Action) {
        ClearSelection -> state = NO_SELECTED_STANZA
        SelectPrevious -> state -= 1
        SelectNext -> state += 1
        is HandleStanzaListOutput -> {
          if (selection == NO_SELECTED_STANZA) setOutput(ClosePoem)
          state = selection
        }
        ExitPoem -> setOutput(ClosePoem)
      }
    }
  }
}
