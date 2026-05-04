@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.mainactivity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.squareup.sample.mainworkflow.TicTacToeWorkflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

class TicTacToeModel(
  private val savedState: SavedStateHandle,
  private val workflow: TicTacToeWorkflow,
) : ViewModel() {
  private val running = Job()

  val renderings: StateFlow<Screen> by lazy {

    renderWorkflowIn(
      workflow = workflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      interceptors = emptyList(),
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    ) {
      running.complete()
    }
  }

  suspend fun waitForExit() = running.join()

  class Factory(
    private val workflow: TicTacToeWorkflow,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
      modelClass: Class<T>,
      extras: CreationExtras
    ): T {
      if (modelClass == TicTacToeModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TicTacToeModel(extras.createSavedStateHandle(), workflow) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
