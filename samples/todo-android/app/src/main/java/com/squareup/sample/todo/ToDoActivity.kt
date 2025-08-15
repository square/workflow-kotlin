@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.todo

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.sample.container.overviewdetail.OverviewDetailContainer
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.withRegistry
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class ToDoActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: ToDoModel by viewModels()

    workflowContentView
      .take(
        lifecycle,
        model.ensureWorkflow().map { it.withRegistry(viewRegistry) }
      )
  }

  private companion object {
    val viewRegistry = ViewRegistry(OverviewDetailContainer)
  }
}

class ToDoModel(private val savedState: SavedStateHandle) : ViewModel() {
  private var renderings: StateFlow<Screen>? = null

  fun ensureWorkflow(): StateFlow<Screen> {
    if (renderings == null) {

      renderings = renderWorkflowIn(
        workflow = TodoListsAppWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState,
        interceptors = emptyList(),
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
      )
    }

    return renderings!!
  }
}
