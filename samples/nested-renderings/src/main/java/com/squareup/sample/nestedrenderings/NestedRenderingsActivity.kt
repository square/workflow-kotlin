/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sample.nestedrenderings

import android.os.Bundle
import androidx.animation.LinearEasing
import androidx.animation.TweenBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.onActive
import androidx.ui.animation.animatedColor
import androidx.ui.graphics.Color
import com.squareup.workflow.diagnostic.SimpleLoggingDiagnosticListener
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.WorkflowRunner
import com.squareup.workflow.ui.compose.withComposeViewFactoryRoot
import com.squareup.workflow.ui.setContentWorkflow

private val viewRegistry = ViewRegistry(
    RecursiveViewFactory,
    LegacyRunner
)

private val viewEnvironment = ViewEnvironment(viewRegistry).withComposeViewFactoryRoot { content ->
  // Animate background color between green and red.
  val color = pulseColor(Color.Green, Color.Red)
  Providers(BackgroundColorAmbient provides color, children = content)
}

class NestedRenderingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentWorkflow(viewEnvironment) {
      WorkflowRunner.Config(
          RecursiveWorkflow,
          diagnosticListener = SimpleLoggingDiagnosticListener()
      )
    }
  }
}

@Composable
private fun pulseColor(
  first: Color,
  second: Color
): Color {
  val color = animatedColor(initVal = first)
  onActive {
    val animation = TweenBuilder<Color>().apply {
      duration = 1000
      easing = LinearEasing
    }

    fun startAnimation() {
      val targetColor = when (color.targetValue) {
        first -> second
        else -> first
      }
      color.animateTo(targetColor, anim = animation) { _, _ -> startAnimation() }
    }
    startAnimation()
  }
  return color.value
}
