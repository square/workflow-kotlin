package com.squareup.sample.mainactivity

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.sample.mainworkflow.TicTacToeWorkflow
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.diagnostic.tracing.TracingWorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File

class TicTacToeModel(
  private val savedState: SavedStateHandle,
  private val workflow: TicTacToeWorkflow,
  private val externalFilesDir: File
) : ViewModel() {
  private val running = Job()

  @OptIn(WorkflowUiExperimentalApi::class)
  val renderings: StateFlow<Any> by lazy {
    val traceFile = externalFilesDir.resolve("workflow-trace-tictactoe.json")

    renderWorkflowIn(
      workflow = workflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      interceptors = listOf(
        object : SimpleLoggingWorkflowInterceptor() {
          override fun log(text: String) = Timber.v(text)
        },
        TracingWorkflowInterceptor(traceFile)
      )
    ) {
      running.complete()
    }
  }

  suspend fun waitForExit() = running.join()

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: TicTacToeWorkflow,
    private val externalFilesDir: File
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == TicTacToeModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TicTacToeModel(handle, workflow, externalFilesDir) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
