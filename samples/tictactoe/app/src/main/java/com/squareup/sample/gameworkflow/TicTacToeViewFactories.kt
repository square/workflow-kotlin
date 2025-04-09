package com.squareup.sample.gameworkflow

import com.squareup.workflow1.ui.ViewRegistry

val TicTacToeViewFactories = ViewRegistry(
  NewGameViewFactory,
  GamePlayViewFactory,
  GameOverLayoutRunner
)
