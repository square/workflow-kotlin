package com.squareup.sample.gameworkflow

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class NewGameScreen(
  val defaultNameX: String,
  val defaultNameO: String,
  val onCancel: () -> Unit,
  val onStartGame: (x: String, o: String) -> Unit
) : Screen
