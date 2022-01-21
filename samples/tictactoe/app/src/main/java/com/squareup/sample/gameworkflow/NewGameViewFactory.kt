package com.squareup.sample.gameworkflow

import com.squareup.sample.tictactoe.databinding.NewGameLayoutBinding
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
internal val NewGameViewFactory: ViewFactory<NewGameScreen> =
  LayoutRunner.bind(NewGameLayoutBinding::inflate) { rendering, _ ->
    if (playerX.text.isBlank()) playerX.setText(rendering.defaultNameX)
    if (playerO.text.isBlank()) playerO.setText(rendering.defaultNameO)

    startGame.setOnClickListener {
      rendering.onStartGame(playerX.text.toString(), playerO.text.toString())
    }

    root.backPressedHandler = { rendering.onCancel() }
  }
