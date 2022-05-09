package com.squareup.benchmarks.performance.complex.poetry

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import androidx.tracing.Trace
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.SimulatedPerfConfig
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.WorkflowUiEventsTracer
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.withEnvironment
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@OptIn(WorkflowUiExperimentalApi::class)
private val viewEnvironment = EMPTY + (ViewRegistry to SampleContainers)

class PerformancePoetryActivity : AppCompatActivity() {

  class NavigationHolder(
    val originScreenName: String?,
    var destinationScreenName: String,
  )

  private val mainHandler = Handler(Looper.getMainLooper())
  private var navigationInFlight: NavigationHolder? = null
  private var lastScreenName: String? = null
  private var frameCount: Long = 0

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Default is just to have the basic 'delay' complexity.
    var simulatedPerfConfig = SimulatedPerfConfig(
      isComplex = true,
      complexityDelay = 200L,
      useInitializingState = false,
      traceLatency = false,
      traceRenderingPasses = false
    )

    intent.extras?.let {
      (it.get(PERF_CONFIG_EXTRA) as? SimulatedPerfConfig)?.let { config ->
        simulatedPerfConfig = config
      }
    }

    require(!(simulatedPerfConfig.traceLatency && simulatedPerfConfig.traceRenderingPasses)) {
      "Only trace latency OR rendering passes."
    }

    // If no interceptor is installed and we are not tracking latency, we will install the tracing
    // interceptor as that is launched from a separate process.
    if (installedInterceptor == null && simulatedPerfConfig.traceRenderingPasses) {
      installedInterceptor = PerformanceTracingInterceptor()
    }

    val component =
      PerformancePoetryComponent(installedInterceptor, simulatedPerfConfig)
    val model: PoetryModel by viewModels { component.poetryModelFactory(this) }

    val instrumentedRenderings = if (simulatedPerfConfig.traceLatency) {
      model.renderings.onEach { screen ->
        traceRenderingLatency(screen)
      }
    } else {
      model.renderings
    }

    setContentView(
      WorkflowLayout(this).apply {
        take(
          lifecycle,
          instrumentedRenderings.map { it.withEnvironment(viewEnvironment) }
        )
      }
    )

    // We can report this here as the first rendering from the Workflow is rendered synchronously.
    this.reportFullyDrawn()
  }

  /**
   * When running this with a benchmark, we can use this to reset anything needed in between
   * benchmark scenarios, even for a HOT start.
   */
  override fun onStart() {
    WorkflowUiEventsTracer.reset()
    (installedInterceptor as? PerformanceTracingInterceptor)?.reset()
    super.onStart()
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  private fun traceRenderingLatency(screen: Screen) {
    WorkflowUiEventsTracer.endTracesForActiveEventsAndClear()

    // Start the trace sections for new rendering produced -> shown.
    val navigationHolder = navigationInFlight
    if (navigationHolder == null) {
      val tag = "$FRAME_LATENCY_SECTION-${frameCount.toString().padStart(2, '0')} "
      Trace.beginAsyncSection(
        tag,
        TRACE_COOKIE
      )
      navigationInFlight = NavigationHolder(
        originScreenName = lastScreenName,
        destinationScreenName = screen.toString(),
      )
      postFrameRenderedCallback {
        navigationInFlight = null
        val endTag = "$FRAME_LATENCY_SECTION-${frameCount.toString().padStart(2, '0')} "
        // End the trace sections for new rendering produced -> shown.
        Trace.endAsyncSection(
          endTag,
          TRACE_COOKIE
        )
        frameCount++
      }
    } else {
      // Keep the previous update and update the destination
      navigationHolder.destinationScreenName = screen.toString()
    }
    lastScreenName = screen.toString()
  }

  private fun postFrameRenderedCallback(block: () -> Unit) {
    // The frame callback runs somewhat in the middle of rendering, so by posting at the front
    // of the queue from there we get the timestamp for right when the next frame is done
    // rendering.
    Choreographer.getInstance().postFrameCallback {
      mainHandler.postAtFrontOfQueue {
        block()
      }
    }
  }

  companion object {
    // Async traces require a unique int not used by any other async trace.
    private const val TRACE_COOKIE = 133742
    const val FRAME_LATENCY_SECTION = "Frame-Latency"
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
  interceptor: WorkflowInterceptor?,
) : ViewModel() {
  @OptIn(WorkflowUiExperimentalApi::class) val renderings: StateFlow<Screen> by lazy {
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
    private val workflowInterceptor: WorkflowInterceptor?,
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
