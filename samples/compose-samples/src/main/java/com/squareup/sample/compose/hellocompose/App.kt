@file:OptIn(WorkflowUiExperimentalApi::class, WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.hellocompose

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState
import com.squareup.workflow1.ui.plus

private val viewEnvironment = ViewEnvironment.EMPTY + ViewRegistry(HelloBinding)

@Composable fun App() {
  MaterialTheme {
    val rendering by HelloWorkflow.renderAsState(
      props = Unit,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig(),
      onOutput = {}
    )
    WorkflowRendering(
      rendering, viewEnvironment,
      Modifier.border(
        shape = RoundedCornerShape(10.dp),
        width = 10.dp,
        color = Color.Magenta
      )
    )
  }
}

@Preview(showBackground = true)
@Composable private fun AppPreview() {
  App()
}
