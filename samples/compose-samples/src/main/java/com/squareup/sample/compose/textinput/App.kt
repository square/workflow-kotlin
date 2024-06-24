@file:OptIn(WorkflowUiExperimentalApi::class, WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.textinput

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.RootScreen
import com.squareup.workflow1.ui.compose.renderAsState
import com.squareup.workflow1.ui.plus

private val viewEnvironment = ViewEnvironment.EMPTY + ViewRegistry(TextInputComposableFactory)

@Composable fun TextInputApp() {
  MaterialTheme {
    val rendering by TextInputWorkflow.renderAsState(
      props = Unit,
      onOutput = {},
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    )
    viewEnvironment.RootScreen(rendering)
  }
}

@Preview(showBackground = true)
@Composable
internal fun TextInputAppPreview() {
  TextInputApp()
}
