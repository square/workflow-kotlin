@file:Suppress("DEPRECATION")

package com.squareup.sample.compose.nestedrenderings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withCompositionRoot
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.renderWorkflowIn
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
        start(
          lifecycle = lifecycle,
          renderings = model.renderings,
          environment = viewEnvironment
        )
      }
    )
  }

  class NestedRenderingsModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Any> by lazy {
      renderWorkflowIn(
        workflow = RecursiveWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState
      )
    }
  }
}
