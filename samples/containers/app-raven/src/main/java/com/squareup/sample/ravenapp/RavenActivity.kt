@file:OptIn(WorkflowUiExperimentalApi::class, WorkflowExperimentalRuntime::class)

package com.squareup.sample.ravenapp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.RealPoemWorkflow
import com.squareup.sample.poetry.model.Raven
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.withRegistry
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

private val viewRegistry = SampleContainers

class RavenActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: RavenModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply {
        take(lifecycle, model.renderings.map { it.withRegistry(viewRegistry) })
      }
    )

    lifecycleScope.launch {
      model.waitForExit()
      finish()
    }
  }

  companion object {
    init {
      Timber.plant(Timber.DebugTree())
    }
  }
}

class RavenModel(savedState: SavedStateHandle) : ViewModel() {
  private val running = Job()

  val renderings: StateFlow<Screen> by lazy {
    renderWorkflowIn(
      workflow = RealPoemWorkflow(),
      scope = viewModelScope,
      savedStateHandle = savedState,
      prop = Raven,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    ) {
      running.complete()
    }
  }

  suspend fun waitForExit() = running.join()
}
