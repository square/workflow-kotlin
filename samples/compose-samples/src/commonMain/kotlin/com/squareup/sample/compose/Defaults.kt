package com.squareup.sample.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

expect fun defaultRuntimeConfig(): RuntimeConfig

@OptIn(WorkflowUiExperimentalApi::class)
expect fun defaultViewEnvironment(): ViewEnvironment

@Composable
expect fun BackHandler(isEnabled: Boolean = true, onBack: () -> Unit)
