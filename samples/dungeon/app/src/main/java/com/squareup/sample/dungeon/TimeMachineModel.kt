package com.squareup.sample.dungeon

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
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
      interceptors = listOf(SimpleLoggingWorkflowInterceptor()),
      // runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig(),
      runtimeConfig = setOf(COMPOSE_RUNTIME),
    )
  }

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: TimeMachineAppWorkflow,
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == TimeMachineModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TimeMachineModel(handle, workflow) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
