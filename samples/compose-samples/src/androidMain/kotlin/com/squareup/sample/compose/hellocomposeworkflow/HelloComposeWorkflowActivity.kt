@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.hellocomposeworkflow

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
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withComposeInteropSupport
import com.squareup.workflow1.ui.renderWorkflowIn
import com.squareup.workflow1.ui.withEnvironment
import kotlinx.coroutines.flow.StateFlow

class HelloComposeWorkflowActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val model: HelloComposeModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply { take(lifecycle, model.renderings) }
    )
  }

  class HelloComposeModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = HelloWorkflow.mapRendering {
          it.withEnvironment(ViewEnvironment.EMPTY.withComposeInteropSupport())
        },
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
      )
    }
  }
}
