package com.squareup.sample.gameworkflow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.sample.tictactoe.databinding.GamePlayLayoutBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.ScreenViewFactory

@OptIn(WorkflowUiExperimentalApi::class)
internal val GamePlayViewFactory: ScreenViewFactory<GamePlayScreen> =
  ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
    GamePlayLayoutBinding.inflate(
      inflater,
      parent,
      attachToParent
    )
  }) { binding ->
    ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
      binding.renderBanner(rendering.gameState, rendering.playerInfo)
      rendering.gameState.board.render(binding.gamePlayBoard.root)
      setCellClickListeners(binding.gamePlayBoard.root, rendering.gameState, rendering.onClick)
      binding.root.backPressedHandler = rendering.onQuit
    }
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
