package com.squareup.sample.gameworkflow

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class GamePlayScreen(
  val playerInfo: PlayerInfo = PlayerInfo(),
  val gameState: Turn = Turn(),
  val onQuit: () -> Unit = {},
  val onClick: (row: Int, col: Int) -> Unit = { _, _ -> }
) : Screen
