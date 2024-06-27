package com.squareup.sample.compose.hellocomposeworkflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.defaultRuntimeConfig
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(showBackground = true)
@Composable
fun HelloComposeWorkflowPreview() {
  val rendering by HelloComposeWorkflow.renderAsState(
    props = "hello",
    onOutput = {},
    runtimeConfig = defaultRuntimeConfig()
  )
  WorkflowRendering(rendering, ViewEnvironment.EMPTY)
}
