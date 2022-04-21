package com.squareup.sample.gameworkflow

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class GameOverScreen(
  val endGameState: RunGameState.GameOver,
  val onTrySaveAgain: () -> Unit,
  val onPlayAgain: () -> Unit,
  val onExit: () -> Unit
) : Screen
