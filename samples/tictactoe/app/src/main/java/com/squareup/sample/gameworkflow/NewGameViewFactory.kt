package com.squareup.sample.gameworkflow

import com.squareup.sample.tictactoe.databinding.NewGameLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.navigation.setBackHandler

@OptIn(WorkflowUiExperimentalApi::class)
internal val NewGameViewFactory: ScreenViewFactory<NewGameScreen> =
  fromViewBinding(NewGameLayoutBinding::inflate) { rendering, _ ->
    if (playerX.text.isBlank()) playerX.setText(rendering.defaultNameX)
    if (playerO.text.isBlank()) playerO.setText(rendering.defaultNameO)

    startGame.setOnClickListener {
      rendering.onStartGame(playerX.text.toString(), playerO.text.toString())
    }

    root.setBackHandler(rendering.onCancel)
  }
