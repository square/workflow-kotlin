@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.hellocomposeworkflow

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.compose.withComposeInteropSupport
import com.squareup.workflow1.ui.withEnvironment
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus

class HelloComposeWorkflowActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val model: HelloComposeModel by viewModels()
    workflowContentView.take(lifecycle, model.renderings)
  }

  class HelloComposeModel(savedState: SavedStateHandle) : ViewModel() {
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = HelloWorkflow.mapRendering {
          it.withEnvironment(ViewEnvironment.EMPTY.withComposeInteropSupport())
        },
        scope = viewModelScope + AndroidUiDispatcher.Main,
        savedStateHandle = savedState,
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
      )
    }
  }
}
