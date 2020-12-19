package com.squareup.sample.dungeon

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.workflow1.diagnostic.tracing.TracingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class TimeMachineModel(
  private val savedState: SavedStateHandle,
  private val workflow: TimeMachineAppWorkflow,
  private val externalFilesDir: File
) : ViewModel() {
  val renderings: StateFlow<Any> by lazy {
    val traceFile = externalFilesDir.resolve("workflow-trace-dungeon.json")

    @OptIn(WorkflowUiExperimentalApi::class)
    renderWorkflowIn(
      workflow = workflow,
      prop = "simple_maze.txt",
      scope = viewModelScope,
      savedStateHandle = savedState,
      interceptors = listOf(TracingWorkflowInterceptor(traceFile))
    )
  }

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: TimeMachineAppWorkflow,
    private val externalFilesDir: File
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == TimeMachineModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TimeMachineModel(handle, workflow, externalFilesDir) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
