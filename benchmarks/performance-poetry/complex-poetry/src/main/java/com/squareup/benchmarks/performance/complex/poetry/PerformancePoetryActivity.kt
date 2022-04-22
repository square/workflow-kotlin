package com.squareup.benchmarks.performance.complex.poetry

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.SimulatedPerfConfig
import com.squareup.benchmarks.performance.complex.poetry.views.LoaderContainer
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.WorkflowInterceptor
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

    // Default.
    var simulatedPerfConfig = SimulatedPerfConfig(
      isComplex = true,
      useInitializingState = true,
      complexityDelay = 200L
    )

    intent.extras?.let {
      (it.get(PERF_CONFIG_EXTRA) as? SimulatedPerfConfig)?.let { config ->
        simulatedPerfConfig = config
      }
    }

    // If no interceptor is installed we will install the tracing interceptor as that is launched
    // from a separate process.
    if (installedInterceptor == null) {
      installedInterceptor = PerformanceTracingInterceptor()
    }
    val component = PerformancePoetryComponent(installedInterceptor, simulatedPerfConfig)
    val model: PoetryModel by viewModels { component.poetryModelFactory(this) }
    setContentView(
      WorkflowLayout(this).apply { start(lifecycle, model.renderings, viewRegistry) }
    )

    // We can report this here as the first rendering from the Workflow is rendered synchronously.
    this.reportFullyDrawn()
  }

  /**
   * When running this with a benchmark, we can use this to reset anything needed in between
   * benchmark scenarios, even for a HOT start.
   */
  override fun onStart() {
    (installedInterceptor as? PerformanceTracingInterceptor)?.let {
      it.reset()
    }
    super.onStart()
  }

  companion object {
    const val PERF_CONFIG_EXTRA = "complex.poetry.performance.config"
    var installedInterceptor: WorkflowInterceptor? = null

    init {
      Timber.plant(Timber.DebugTree())
    }
  }
}

class PoetryModel(
  savedState: SavedStateHandle,
  workflow: MaybeLoadingGatekeeperWorkflow<List<Poem>>,
  interceptor: WorkflowInterceptor?
) : ViewModel() {
  @OptIn(WorkflowUiExperimentalApi::class) val renderings: StateFlow<Any> by lazy {
    renderWorkflowIn(
      workflow = workflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      interceptors = interceptor?.let { listOf(it) } ?: emptyList()
    )
  }

  class Factory(
    owner: SavedStateRegistryOwner,
    private val workflow: MaybeLoadingGatekeeperWorkflow<List<Poem>>,
    private val workflowInterceptor: WorkflowInterceptor?
  ) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
      key: String,
      modelClass: Class<T>,
      handle: SavedStateHandle
    ): T {
      if (modelClass == PoetryModel::class.java) {
        @Suppress("UNCHECKED_CAST")
        return PoetryModel(handle, workflow, workflowInterceptor) as T
      }

      throw IllegalArgumentException("Unknown ViewModel type $modelClass")
    }
  }
}
