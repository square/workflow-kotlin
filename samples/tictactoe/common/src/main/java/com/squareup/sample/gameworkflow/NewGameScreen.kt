package com.squareup.sample.gameworkflow

data class NewGameScreen(
  val defaultNameX: String,
  val defaultNameO: String,
  val onCancel: () -> Unit,
  val onStartGame: (x: String, o: String) -> Unit
)
