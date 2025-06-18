@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.inlinerendering

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.compose.withComposeInteropSupport
import com.squareup.workflow1.ui.renderWorkflowIn
import com.squareup.workflow1.ui.withEnvironment
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow

/**
 * A workflow that returns an anonymous
 * [ComposeScreen][com.squareup.workflow1.ui.compose.ComposeScreen].
 */
class InlineRenderingActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: HelloBindingModel by viewModels()
    workflowContentView.take(lifecycle, model.renderings)
  }

  class HelloBindingModel(savedState: SavedStateHandle) : ViewModel() {
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = InlineRenderingWorkflow.mapRendering {
          it.withEnvironment(ViewEnvironment.EMPTY.withComposeInteropSupport())
        },
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
      )
    }
  }
}
