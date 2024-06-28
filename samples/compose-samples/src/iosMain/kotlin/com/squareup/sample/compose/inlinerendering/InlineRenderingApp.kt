package com.squareup.sample.compose.inlinerendering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.squareup.sample.compose.defaultViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState

@OptIn(WorkflowUiExperimentalApi::class)
@Composable
fun InlineRenderingApp() {
  val rendering by InlineRenderingWorkflow.renderAsState(Unit) {}
  WorkflowRendering(rendering, defaultViewEnvironment())
}
