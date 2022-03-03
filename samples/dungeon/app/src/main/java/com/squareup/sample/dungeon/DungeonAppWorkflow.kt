package com.squareup.sample.dungeon

import com.squareup.sample.dungeon.DungeonAppWorkflow.Props
import com.squareup.sample.dungeon.DungeonAppWorkflow.State
import com.squareup.sample.dungeon.DungeonAppWorkflow.State.ChoosingBoard
import com.squareup.sample.dungeon.DungeonAppWorkflow.State.LoadingBoardList
import com.squareup.sample.dungeon.DungeonAppWorkflow.State.PlayingGame
import com.squareup.sample.dungeon.board.Board
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen

@OptIn(WorkflowUiExperimentalApi::class)
class DungeonAppWorkflow(
  private val gameSessionWorkflow: GameSessionWorkflow,
  private val boardLoader: BoardLoader
) : StatefulWorkflow<Props, State, Nothing, DungeonRootUi>() {

  data class Props(val paused: Boolean = false)

  sealed class State {
    object LoadingBoardList : State(), Screen
    data class ChoosingBoard(val boards: List<Pair<String, Board>>) : State()
    data class PlayingGame(val boardPath: BoardPath) : State()
  }

  data class DisplayBoardsListScreen(
    val boards: List<Board>,
    val onBoardSelected: (index: Int) -> Unit
  ) : Screen

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State = LoadingBoardList

  override fun render(
    renderProps: Props,
    renderState: State,
    context: RenderContext
  ): DungeonRootUi = when (renderState) {

    LoadingBoardList -> {
      context.runningWorker(boardLoader.loadAvailableBoards()) { displayBoards(it) }
      AlertContainerScreen(renderState)
    }

    is ChoosingBoard -> {
      val screen = DisplayBoardsListScreen(
        boards = renderState.boards.map { it.second },
        onBoardSelected = { index -> context.actionSink.send(selectBoard(index)) }
      )
      AlertContainerScreen(screen)
    }

    is PlayingGame -> {
      val sessionProps = GameSessionWorkflow.Props(renderState.boardPath, renderProps.paused)
      val gameScreen = context.renderChild(gameSessionWorkflow, sessionProps)
      gameScreen
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun displayBoards(boards: Map<String, Board>) = action {
    state = ChoosingBoard(boards.toList())
  }

  private fun selectBoard(index: Int) = action {
    // No-op if we're not in the ChoosingBoard state.
    val boards = (state as? ChoosingBoard)?.boards ?: return@action
    state = PlayingGame(boards[index].first)
  }
}
