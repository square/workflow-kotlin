@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.mainactivity

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.sample.mainworkflow.TicTacToeWorkflow
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
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
      runtimeConfig = setOf(RuntimeConfigOptions.COMPOSE_RUNTIME) //AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    ) {
      running.complete()
    }
  }

  suspend fun waitForExit() = running.join()

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: TicTacToeWorkflow,
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == TicTacToeModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TicTacToeModel(handle, workflow) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
