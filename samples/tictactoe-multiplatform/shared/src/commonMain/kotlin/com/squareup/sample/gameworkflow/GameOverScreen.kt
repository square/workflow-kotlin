package com.squareup.sample.gameworkflow

import com.squareup.sample.gameworkflow.RunGameState.GameOver

data class GameOverScreen(
  val endGameState: GameOver,
  val onTrySaveAgain: () -> Unit,
  val onPlayAgain: () -> Unit,
  val onExit: () -> Unit
)
