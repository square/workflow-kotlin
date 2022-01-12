@file:OptIn(WorkflowUiExperimentalApi::class)
@file:Suppress("FunctionName")

package com.squareup.sample.compose.textinput

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState
import com.squareup.workflow1.ui.plus

private val viewEnvironment = ViewEnvironment.EMPTY + ViewRegistry(TextInputViewFactory)

@Composable fun TextInputApp() {
  MaterialTheme {
    val rendering by TextInputWorkflow.renderAsState(props = Unit, onOutput = {})
    WorkflowRendering(rendering, viewEnvironment)
  }
}

@Preview(showBackground = true)
@Composable internal fun TextInputAppPreview() {
  TextInputApp()
}
