package com.squareup.sample.gameworkflow

import com.squareup.workflow1.ui.Screen

data class NewGameScreen(
  val defaultNameX: String,
  val defaultNameO: String,
  val onCancel: () -> Unit,
  val onStartGame: (x: String, o: String) -> Unit
) : Screen
