package com.squareup.sample.gameworkflow

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewRegistry

@OptIn(WorkflowUiExperimentalApi::class)
val TicTacToeViewFactories = ViewRegistry(
    NewGameViewFactory,
    GamePlayViewFactory,
    GameOverLayoutUpdater
)
