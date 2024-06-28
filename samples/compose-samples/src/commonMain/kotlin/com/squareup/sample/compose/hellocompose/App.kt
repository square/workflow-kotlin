@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.compose.hellocompose

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squareup.sample.compose.defaultRuntimeConfig
import com.squareup.sample.compose.defaultViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState

private val viewEnvironment = defaultViewEnvironment()

@Composable fun App() {
  MaterialTheme {
    val rendering by HelloComposeWorkflow.renderAsState(
      props = Unit,
      runtimeConfig = defaultRuntimeConfig(),
      onOutput = {}
    )
    WorkflowRendering(
      rendering,
      viewEnvironment,
      Modifier.border(
        shape = RoundedCornerShape(10.dp),
        width = 10.dp,
        color = Color.Magenta
      )
    )
  }
}
