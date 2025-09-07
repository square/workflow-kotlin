@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.inlinerendering

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState

@OptIn(WorkflowExperimentalApi::class)
object InlineRenderingWorkflow : ComposeWorkflow<Unit, Nothing, Screen>() {

  @Composable
  override fun produceRendering(
    props: Unit,
    emitOutput: (Nothing) -> Unit
  ): Screen {
    var state by rememberSaveable { mutableStateOf(0) }
    return ComposeScreen {
      Content(state, onClick = { state++ })
    }
  }
}

@Composable
private fun Content(
  count: Int,
  onClick: () -> Unit
) {
  Box {
    Button(onClick = onClick) {
      Text("Counter: ")
      AnimatedCounter(count) { counterValue ->
        Text(counterValue.toString())
      }
    }
  }
}

@Composable
fun InlineRenderingWorkflowRendering() {
  val rendering by InlineRenderingWorkflow.renderAsState(
    props = Unit,
    onOutput = {},
    runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
  )
  WorkflowRendering(rendering)
}

@Preview(showBackground = true)
@Composable
internal fun InlineRenderingWorkflowPreview() {
  InlineRenderingWorkflowRendering()
}

@Composable
private fun AnimatedCounter(
  counterValue: Int,
  content: @Composable (Int) -> Unit
) {
  AnimatedContent(
    targetState = counterValue,
    transitionSpec = {
      ((slideInVertically() + fadeIn()).togetherWith(slideOutVertically() + fadeOut()))
        .using(SizeTransform(clip = false))
    },
    label = ""
  ) { content(it) }
}
