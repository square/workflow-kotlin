@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.stubvisibility

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow

class StubVisibilityActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: StubVisibilityModel by viewModels()
    workflowContentView.take(lifecycle, model.renderings)
  }
}

class StubVisibilityModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<Screen> by lazy {
    renderWorkflowIn(
      workflow = StubVisibilityWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    )
  }
}
