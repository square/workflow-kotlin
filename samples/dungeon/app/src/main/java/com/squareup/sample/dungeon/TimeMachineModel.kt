package com.squareup.sample.dungeon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.ExperimentalTime

class TimeMachineModel(
  private val savedState: SavedStateHandle,
  private val workflow: TimeMachineAppWorkflow,
) : ViewModel() {
  @OptIn(ExperimentalTime::class, WorkflowExperimentalRuntime::class)
  val renderings: StateFlow<Screen> by lazy {

    renderWorkflowIn(
      workflow = workflow,
      prop = "simple_maze.txt",
      scope = viewModelScope,
      savedStateHandle = savedState,
      interceptors = emptyList(),
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    )
  }

  class Factory(
    private val workflow: TimeMachineAppWorkflow,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
      modelClass: Class<T>,
      extras: CreationExtras
    ): T {
      if (modelClass == TimeMachineModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TimeMachineModel(extras.createSavedStateHandle(), workflow) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
