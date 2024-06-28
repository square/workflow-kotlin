package com.squareup.sample.compose.hellocomposeworkflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.squareup.sample.compose.hellocomposeworkflow.HelloComposeWorkflow.Toggle
import com.squareup.workflow1.Sink
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A [ComposeWorkflow] that is used by [HelloWorkflow] to render the screen.
 *
 * This workflow has type `Workflow<String, Toggle, ComposeRendering>`.
 */
@OptIn(WorkflowUiExperimentalApi::class)
object HelloComposeWorkflow : ComposeWorkflow<String, Toggle>() {

  object Toggle

  @Composable override fun RenderingContent(
    props: String,
    outputSink: Sink<Toggle>,
    viewEnvironment: ViewEnvironment
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
