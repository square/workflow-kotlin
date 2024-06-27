package com.squareup.sample.compose.nestedrenderings

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class StringRendering(val value: String) : Screen
