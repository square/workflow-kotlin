package com.squareup.sample.compose

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

expect fun defaultRuntimeConfig(): RuntimeConfig

@OptIn(WorkflowUiExperimentalApi::class)
expect fun defaultViewEnvironment(): ViewEnvironment
