package com.squareup.sample.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withComposeInteropSupport

@OptIn(WorkflowExperimentalRuntime::class)
actual fun defaultRuntimeConfig(): RuntimeConfig =
  AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()

@OptIn(WorkflowUiExperimentalApi::class)
actual fun defaultViewEnvironment(): ViewEnvironment =
  ViewEnvironment.EMPTY.withComposeInteropSupport()

@Composable
actual fun BackHandler(isEnabled: Boolean, onBack: () -> Unit) =
  androidx.activity.compose.BackHandler(isEnabled, onBack)
