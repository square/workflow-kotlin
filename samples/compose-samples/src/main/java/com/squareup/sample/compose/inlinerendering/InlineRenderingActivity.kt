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
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

/**
 * A workflow that returns an anonymous `ComposeRendering`.
 */
class InlineRenderingActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: HelloBindingModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply { take(lifecycle, model.renderings) }
    )
  }

  class HelloBindingModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = InlineRenderingWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
      )
    }
  }
}
