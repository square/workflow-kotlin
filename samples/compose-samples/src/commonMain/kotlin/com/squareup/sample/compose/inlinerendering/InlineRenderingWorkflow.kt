@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.compose.inlinerendering

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.squareup.sample.compose.defaultRuntimeConfig
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ComposeScreen
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.renderAsState

object InlineRenderingWorkflow : StatefulWorkflow<Unit, Int, Nothing, Screen>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): Int = snapshot?.bytes?.parse { it.readInt() } ?: 0

  override fun render(
    renderProps: Unit,
    renderState: Int,
    context: RenderContext
  ) = ComposeScreen {
    Box {
      Button(onClick = context.eventHandler { state += 1 }) {
        Text("Counter: ")
        AnimatedCounter(renderState) { counterValue ->
          Text(counterValue.toString())
        }
      }
    }
  }

  override fun snapshotState(state: Int): Snapshot = Snapshot.of(state)
}

@Composable
fun InlineRenderingWorkflowRendering() {
  val rendering by InlineRenderingWorkflow.renderAsState(
    props = Unit,
    onOutput = {},
    runtimeConfig = defaultRuntimeConfig()
  )
  WorkflowRendering(rendering, ViewEnvironment.EMPTY)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedCounter(
  counterValue: Int,
  content: @Composable (Int) -> Unit
) {
  AnimatedContent(
    targetState = counterValue,
    transitionSpec = {
      (slideInVertically() + fadeIn() with slideOutVertically() + fadeOut())
        .using(SizeTransform(clip = false))
    }
  ) { content(it) }
}
