package com.squareup.sample.gameworkflow

import android.view.View
import android.view.ViewGroup
import com.squareup.sample.tictactoe.databinding.GamePlayLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
internal val GamePlayViewFactory: ScreenViewFactory<GamePlayScreen> =
  ScreenViewRunner.bind(GamePlayLayoutBinding::inflate) { rendering, _ ->
    renderBanner(rendering.gameState, rendering.playerInfo)
    rendering.gameState.board.render(gamePlayBoard.root)

    setCellClickListeners(gamePlayBoard.root, rendering.gameState, rendering.onClick)
    root.backPressedHandler = rendering.onQuit
  }

private fun setCellClickListeners(
  viewGroup: ViewGroup,
  turn: Turn,
  takeSquareHandler: (row: Int, col: Int) -> Unit
) {
  for (i in 0..8) {
    val cell = viewGroup.getChildAt(i)

    val row = i / 3
    val col = i % 3
    val box = turn.board[row][col]

    val cellClickListener =
      if (box != null) null
      else View.OnClickListener { takeSquareHandler(row, col) }

    cell.setOnClickListener(cellClickListener)
  }
}

private fun GamePlayLayoutBinding.renderBanner(
  turn: Turn,
  playerInfo: PlayerInfo
) {
  val mark = turn.playing.symbol
  val playerName = turn.playing.name(playerInfo)
    .trim()

  gamePlayToolbar.title = when {
    playerName.isEmpty() -> "Place your $mark"
    else -> "$playerName, place your $mark"
  }
}
