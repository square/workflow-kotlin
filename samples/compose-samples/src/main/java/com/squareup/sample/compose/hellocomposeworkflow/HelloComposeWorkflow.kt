@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.hellocomposeworkflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.hellocomposeworkflow.HelloComposeWorkflow.Toggle
import com.squareup.workflow1.Sink
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState

/**
 * A [ComposeWorkflow] that is used by [HelloWorkflow] to render the screen.
 *
 * This workflow has type `Workflow<String, Toggle, ComposeRendering>`.
 */
object HelloComposeWorkflow : ComposeWorkflow<String, Toggle>() {

  object Toggle

  @Composable override fun RenderingContent(
    props: String,
    outputSink: Sink<Toggle>,
  ) {
    MaterialTheme {
      Text(
        text = props,
        modifier = Modifier
          .clickable(onClick = { outputSink.send(Toggle) })
          .fillMaxSize()
          .wrapContentSize(Alignment.Center)
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HelloComposeWorkflowPreview() {
  val rendering by HelloComposeWorkflow.renderAsState(
    props = "hello",
    onOutput = {},
    runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
  )
  WorkflowRendering(rendering)
}
