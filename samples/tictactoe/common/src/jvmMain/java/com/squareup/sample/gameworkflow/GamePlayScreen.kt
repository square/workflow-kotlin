package com.squareup.sample.gameworkflow

data class GamePlayScreen(
  val playerInfo: PlayerInfo = PlayerInfo(),
  val gameState: Turn = Turn(),
  val onQuit: () -> Unit = {},
  val onClick: (row: Int, col: Int) -> Unit = { _, _ -> }
)
