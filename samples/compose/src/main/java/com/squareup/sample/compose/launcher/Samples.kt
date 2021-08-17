@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.sample.compose.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.squareup.sample.compose.hellocomposebinding.DrawHelloRenderingPreview
import com.squareup.sample.compose.hellocomposebinding.HelloBindingActivity
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class) val samples = listOf(
  Sample(
    "Hello Compose Binding", HelloBindingActivity::class,
    "Creates a ViewFactory using bindCompose."
  ) { DrawHelloRenderingPreview() },
)

data class Sample(
  val name: String,
  val activityClass: KClass<out ComponentActivity>,
  val description: String,
  val preview: @Composable () -> Unit
)
