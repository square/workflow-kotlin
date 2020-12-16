package com.squareup.sample.ravenapp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.PoemWorkflow
import com.squareup.sample.poetry.model.Raven
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = SampleContainers + BackStackContainer

class RavenActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: RavenModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply { start(model.renderings, viewRegistry) }
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

  @OptIn(WorkflowUiExperimentalApi::class)
  val renderings: StateFlow<Any> by lazy {
    renderWorkflowIn(
      workflow = PoemWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      prop = Raven
    ) {
      running.complete()
    }
  }

  suspend fun waitForExit() = running.join()
}
