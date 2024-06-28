package com.squareup.sample.compose.launcher

import com.squareup.sample.compose.inlinerendering.InlineRenderingWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
val samples = listOf(
  Sample(
    "Compose Workflow",
    "Demonstrates a special implementation of Workflow that lets the workflow define " +
      "its own composable content inline.",
    com.squareup.sample.compose.hellocomposeworkflow.HelloWorkflow
  ),
  Sample(
    "Hello Compose",
    "A pure Compose app that launches its root Workflow from inside Compose.",
    com.squareup.sample.compose.hellocompose.HelloComposeWorkflow
  ),
  Sample(
    "Hello Compose Binding",
    "Binds a Screen to a UI factory using ScreenComposableFactory().",
    com.squareup.sample.compose.hellocomposebinding.HelloWorkflow
  ),

  // TODO: Migrate :workflow-ui:compose-tooling to be multiplatform to display this sample
  // since both of these samples utilize the special preview view environments
  // Sample(
  //   "Text Input",
  //   "Demonstrates a workflow that drives a TextField.",
  //   TextInputWorkflow
  // ),
  //
  //
  // Sample(
  //   "ViewFactory Preview",
  //   "Demonstrates displaying @Previews of ViewFactories.",
  //   ComposeRendering(previewContactRendering)
  // ),
  Sample(
    "Inline ComposeRendering",
    "Demonstrates a workflow that returns an anonymous ComposeRendering.",
    InlineRenderingWorkflow
  )
)

data class Sample @OptIn(WorkflowUiExperimentalApi::class) constructor(
  val name: String,
  val description: String,
  val workflow: Workflow<*, *, Screen>
)
