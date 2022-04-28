package com.squareup.sample.dungeon

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.workflow1.diagnostic.tracing.TracingWorkflowInterceptor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.time.ExperimentalTime

class TimeMachineModel(
  private val savedState: SavedStateHandle,
  private val workflow: TimeMachineAppWorkflow,
  private val traceFilesDir: File
) : ViewModel() {
  @OptIn(WorkflowUiExperimentalApi::class, ExperimentalTime::class)
  val renderings: StateFlow<Screen> by lazy {
    val traceFile = traceFilesDir.resolve("workflow-trace-dungeon.json")

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
    private val traceFilesDir: File
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == TimeMachineModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TimeMachineModel(handle, workflow, traceFilesDir) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
