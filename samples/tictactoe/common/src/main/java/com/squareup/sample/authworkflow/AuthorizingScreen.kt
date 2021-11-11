package com.squareup.sample.authworkflow

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class AuthorizingScreen(
  val message: String
) : Screen
