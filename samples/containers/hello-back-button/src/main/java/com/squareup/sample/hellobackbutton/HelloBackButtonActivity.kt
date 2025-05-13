@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.hellobackbutton

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.squareup.sample.container.SampleContainers
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.navigation.reportNavigation
import com.squareup.workflow1.ui.renderWorkflowIn
import com.squareup.workflow1.ui.withRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

private val viewRegistry = SampleContainers

class HelloBackButtonActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: HelloBackButtonModel by viewModels()
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

class HelloBackButtonModel(savedState: SavedStateHandle) : ViewModel() {
  private val running = Job()

  val renderings: Flow<Screen> by lazy {
    renderWorkflowIn(
      workflow = AreYouSureWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    ) {
      // This workflow handles the back button itself, so the activity can't.
      // Instead, the workflow emits an output to signal that it's time to shut things down.
      running.complete()
    }.reportNavigation {
      Timber.i("Navigated to %s", it)
    }
  }

  /** Blocks until the workflow signals that it's time to shut things down. */
  suspend fun waitForExit() = running.join()
}
