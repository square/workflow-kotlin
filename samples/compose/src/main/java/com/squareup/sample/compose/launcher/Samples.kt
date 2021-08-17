@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.sample.compose.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.squareup.sample.hellocompose.App
import com.squareup.sample.hellocompose.HelloComposeActivity
import com.squareup.sample.compose.hellocomposebinding.DrawHelloRenderingPreview
import com.squareup.sample.compose.hellocomposebinding.HelloBindingActivity
import com.squareup.sample.hellocomposerendering.HelloComposeRenderingActivity
import com.squareup.sample.hellocomposerendering.HelloRenderingWorkflowPreview
import com.squareup.sample.compose.nestedrenderings.NestedRenderingsActivity
import com.squareup.sample.compose.nestedrenderings.RecursiveViewFactoryPreview
import com.squareup.sample.preview.PreviewActivity
import com.squareup.sample.preview.PreviewApp
import com.squareup.sample.textinput.TextInputActivity
import com.squareup.sample.textinput.TextInputAppPreview
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class) val samples = listOf(
  Sample(
    "Hello Compose Binding", HelloBindingActivity::class,
    "Creates a ViewFactory using bindCompose."
  ) { DrawHelloRenderingPreview() },
  Sample(
    "Hello Compose Rendering", HelloComposeRenderingActivity::class,
    "Uses ComposeWorkflow to create a workflow that draws itself."
  ) { HelloRenderingWorkflowPreview() },
  Sample(
    "Hello Compose", HelloComposeActivity::class,
    "A pure Compose app that launches its root Workflow from inside Compose."
  ) { App() },
  Sample(
    "Nested Renderings", NestedRenderingsActivity::class,
    "Demonstrates recursive view factories using both Compose and legacy view factories."
  ) { RecursiveViewFactoryPreview() },
  Sample(
    "ViewFactory Preview", PreviewActivity::class,
    "Demonstrates displaying @Previews of ViewFactories."
  ) { PreviewApp() },
  Sample(
    "Text Input", TextInputActivity::class,
    "Demonstrates a workflow that drives a TextField."
  ) { TextInputAppPreview() },
)

data class Sample(
  val name: String,
  val activityClass: KClass<out ComponentActivity>,
  val description: String,
  val preview: @Composable () -> Unit
)
