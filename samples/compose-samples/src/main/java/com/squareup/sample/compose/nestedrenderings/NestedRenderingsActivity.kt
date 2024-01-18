@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.nestedrenderings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withCompositionRoot
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.renderWorkflowIn
import com.squareup.workflow1.ui.withEnvironment
import kotlinx.coroutines.flow.StateFlow

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = ViewRegistry(
  RecursiveViewFactory,
  LegacyRunner
)

@OptIn(WorkflowUiExperimentalApi::class)
private val viewEnvironment =
  (ViewEnvironment.EMPTY + viewRegistry).withCompositionRoot { content ->
    CompositionLocalProvider(LocalBackgroundColor provides Color.Green) {
      content()
    }
  }

@WorkflowUiExperimentalApi
class NestedRenderingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: NestedRenderingsModel by viewModels()
    setContentView(
      WorkflowLayout(this).apply {
        take(
          lifecycle = lifecycle,
          renderings = model.renderings,
        )
      }
    )
  }

  class NestedRenderingsModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = RecursiveWorkflow.mapRendering { it.withEnvironment(viewEnvironment) },
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
      )
    }
  }
}
