package com.squareup.sample.compose

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

actual fun defaultRuntimeConfig(): RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG

@OptIn(WorkflowUiExperimentalApi::class)
actual fun defaultViewEnvironment(): ViewEnvironment = ViewEnvironment.EMPTY
