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
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.parse
import com.squareup.workflow1.ui.Screen
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
  ): ComposeScreen {
    val onClick = context.eventHandler("increment") { state += 1 }
    return ComposeScreen {
      Box {
        Button(onClick = onClick) {
          Text("Counter: ")
          AnimatedCounter(renderState) { counterValue ->
            Text(counterValue.toString())
          }
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
