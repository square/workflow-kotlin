package com.squareup.benchmarks.performance.complex.poetry

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.benchmarks.performance.complex.poetry.views.LoaderContainer
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = SampleContainers + BackStackContainer + LoaderContainer

class PerformancePoetryActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val component: PerformancePoetryComponent by viewModels()
    val model: PoetryModel by viewModels { component.poetryModelFactory(this) }
    setContentView(
      WorkflowLayout(this).apply { start(lifecycle, model.renderings, viewRegistry) }
    )
  }

  companion object {
    init {
      Timber.plant(Timber.DebugTree())
    }
  }
}

class PoetryModel(
  savedState: SavedStateHandle,
  workflow: MaybeLoadingGatekeeperWorkflow<List<Poem>>
) : ViewModel() {
  @OptIn(WorkflowUiExperimentalApi::class) val renderings: StateFlow<Any> by lazy {
    renderWorkflowIn(
      workflow = workflow,
      scope = viewModelScope,
      savedStateHandle = savedState
    )
  }

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: MaybeLoadingGatekeeperWorkflow<List<Poem>>
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel?> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == PoetryModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return PoetryModel(handle, workflow) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
