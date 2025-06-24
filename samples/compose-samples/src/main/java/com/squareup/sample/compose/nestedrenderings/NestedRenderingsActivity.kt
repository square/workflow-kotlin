@file:OptIn(WorkflowExperimentalRuntime::class)

package com.squareup.sample.compose.nestedrenderings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.RuntimeConfigOptions.COMPOSE_RUNTIME
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.compose.withComposeInteropSupport
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.withEnvironment
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus

private val viewRegistry = ViewRegistry(RecursiveComposableFactory)

private val viewEnvironment =
  (ViewEnvironment.EMPTY + viewRegistry)
    .withComposeInteropSupport { content ->
      CompositionLocalProvider(LocalBackgroundColor provides Color.Green) {
        content()
      }
    }

class NestedRenderingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: NestedRenderingsModel by viewModels()
    workflowContentView.take(lifecycle = lifecycle, renderings = model.renderings)
  }

  class NestedRenderingsModel(savedState: SavedStateHandle) : ViewModel() {
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = RecursiveWorkflow.mapRendering { it.withEnvironment(viewEnvironment) },
        scope = viewModelScope + AndroidUiDispatcher.Main,
        savedStateHandle = savedState,
        runtimeConfig = setOf(COMPOSE_RUNTIME), // AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()
        interceptors = listOf(SimpleLoggingWorkflowInterceptor()),
      )
    }
  }
}
