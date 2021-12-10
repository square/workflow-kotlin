package com.squareup.sample.mainactivity

import android.view.Choreographer
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import app.cash.molecule.AndroidUiFrameClock
import app.cash.molecule.launchMolecule
import com.squareup.sample.mainworkflow.TicTacToeComposeWorkflow
import com.squareup.workflow.compose.render
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import java.io.File

class TicTacToeModel(
  private val savedState: SavedStateHandle,
  private val workflow: TicTacToeComposeWorkflow,
  private val traceFilesDir: File
) : ViewModel() {
  private val running = Job()

  @OptIn(WorkflowUiExperimentalApi::class)
  val renderings: StateFlow<Any> by lazy {
    val traceFile = traceFilesDir.resolve("workflow-trace-tictactoe.json")

    (viewModelScope + AndroidUiFrameClock(Choreographer.getInstance())).launchMolecule {
      workflow.render { running.complete() }
    }
    // renderWorkflowIn(
    //   workflow = workflow,
    //   scope = viewModelScope,
    //   savedStateHandle = savedState,
    //   interceptors = listOf(TracingWorkflowInterceptor(traceFile))
    // ) {
    //   running.complete()
    // }
  }

  suspend fun waitForExit() = running.join()

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: TicTacToeComposeWorkflow,
    private val traceFilesDir: File
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == TicTacToeModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return TicTacToeModel(handle, workflow, traceFilesDir) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
