@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.poetryapp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.sample.container.SampleContainers
import com.squareup.sample.poetry.RealPoemWorkflow
import com.squareup.sample.poetry.RealPoemsBrowserWorkflow
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.reportNavigation
import com.squareup.workflow1.ui.withRegistry
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val viewRegistry = SampleContainers

class PoetryActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: PoetryModel by viewModels()
    workflowContentView.take(lifecycle, model.renderings.map { it.withRegistry(viewRegistry) })
  }

  companion object {
    init {
      Timber.plant(Timber.DebugTree())
    }
  }
}

class PoetryModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: Flow<Screen> by lazy {
    renderWorkflowIn(
      workflow = RealPoemsBrowserWorkflow(RealPoemWorkflow()),
      scope = viewModelScope,
      prop = 0 to 0 to Poem.allPoems,
      savedStateHandle = savedState,
      runtimeConfig = setOf(COMPOSE_RUNTIME), // AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    ).reportNavigation {
      Timber.i("Navigated to %s", it)
    }
  }
}
