package com.squareup.sample.compose.hellocomposeworkflow

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

class HelloComposeWorkflowActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val model: HelloComposeModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply { start(model.renderings) }
    )
  }

  class HelloComposeModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Any> by lazy {
      renderWorkflowIn(
        workflow = HelloWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState
      )
    }
  }
}
