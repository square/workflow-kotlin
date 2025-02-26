@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.hellocomposeworkflow

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.renderWorkflowIn
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow

class HelloComposeWorkflowActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // This ViewModel will survive configuration changes. It's instantiated
    // by the first call to viewModels(), and that original instance is returned by
    // succeeding calls.
    val model: HelloViewModel by viewModels()
    workflowContentView.take(lifecycle, model.renderings)
  }
}

class HelloViewModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<HelloRendering> by lazy {
    renderWorkflowIn(
      workflow = HelloComposeWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig(),
      interceptors = listOf(SimpleLoggingWorkflowInterceptor())
    )
  }
}
