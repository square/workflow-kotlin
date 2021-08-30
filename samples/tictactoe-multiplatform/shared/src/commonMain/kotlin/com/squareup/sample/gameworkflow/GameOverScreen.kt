package com.squareup.sample.gameworkflow

data class GameOverScreen(
  val endGameState: RunGameState.GameOver,
  val onTrySaveAgain: () -> Unit,
  val onPlayAgain: () -> Unit,
  val onExit: () -> Unit
)
