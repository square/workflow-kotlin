@file:Suppress("DEPRECATION")

package com.squareup.sample.compose.hellocomposebinding

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.composeViewFactory
import com.squareup.workflow1.ui.compose.withCompositionRoot
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

@OptIn(WorkflowUiExperimentalApi::class)
private val viewEnvironment =
  (ViewEnvironment.EMPTY + ViewRegistry(HelloBinding)).withCompositionRoot { content ->
    MaterialTheme(content = content)
  }

/**
 * Demonstrates how to create and display a view factory with [composeViewFactory].
 */
class HelloBindingActivity : AppCompatActivity() {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: HelloBindingModel by viewModels()
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

  class HelloBindingModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Any> by lazy {
      renderWorkflowIn(
        workflow = HelloWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState
      )
    }
  }
}
