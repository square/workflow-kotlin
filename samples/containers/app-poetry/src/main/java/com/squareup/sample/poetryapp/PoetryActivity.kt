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
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.withRegistry
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = SampleContainers

class PoetryActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: PoetryModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply {
        take(lifecycle, model.renderings.map { it.withRegistry(viewRegistry) })
      }
    )
  }

  companion object {
    init {
      Timber.plant(Timber.DebugTree())
    }
  }
}

class PoetryModel(savedState: SavedStateHandle) : ViewModel() {
  @OptIn(WorkflowUiExperimentalApi::class)
  val renderings: StateFlow<Screen> by lazy {
    renderWorkflowIn(
      workflow = RealPoemsBrowserWorkflow(RealPoemWorkflow()),
      scope = viewModelScope,
      prop = 0 to 0 to Poem.allPoems,
      savedStateHandle = savedState,
      runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
    )
  }
}
