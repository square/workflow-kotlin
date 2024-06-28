@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.compose.launcher

import androidx.compose.runtime.Composable
import com.squareup.sample.compose.BackHandler
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow
import com.squareup.sample.compose.hellocomposebinding.viewEnvironment
import com.squareup.sample.compose.hellocomposeworkflow.HelloComposeWorkflow
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.withEnvironment

object SampleWorkflow : StatefulWorkflow<Unit, Sample?, Nothing, Screen>() {
  override fun render(
    renderProps: Unit,
    renderState: Sample?,
    context: RenderContext
  ): Screen {
    val screen = when (val workflow = renderState?.workflow) {
      null -> SampleScreen(context.eventHandler { sample -> state = sample })
      is HelloComposeWorkflow -> context.renderChild(
        child = workflow,
        props = "Hello",
      ) { noAction() }

      is HelloWorkflow -> context.renderChild(
        child = workflow.mapRendering { it.withEnvironment(viewEnvironment) }
      ) { noAction() }

      else -> context.renderChild(child = workflow as Workflow<Unit, Nothing, Screen>)
    }

    return BackHandlerScreen(screen, onBack = context.eventHandler { state = null })
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ) = null

  override fun snapshotState(state: Sample?): Snapshot? = null
}

data class BackHandlerScreen(val screen: Screen, val onBack: () -> Unit) : ComposeScreen {
  @Composable
  override fun Content(viewEnvironment: ViewEnvironment) {
    BackHandler(onBack = onBack)
    WorkflowRendering(screen, viewEnvironment)
  }
}
