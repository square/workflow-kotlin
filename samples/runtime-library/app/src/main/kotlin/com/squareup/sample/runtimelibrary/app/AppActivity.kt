package com.squareup.sample.runtimelibrary.app

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.sample.runtimelibrary.lib.checkJvmLinkage
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.config.AndroidRuntimeConfigTools
import com.squareup.workflow1.internal.withKey
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.compose.withComposeInteropSupport
import com.squareup.workflow1.ui.withEnvironment
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

private val viewEnvironment = ViewEnvironment.EMPTY.withComposeInteropSupport()

class AppActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // This method is defined by both Android and JVM targets.
    RuntimeException().withKey("foo")

    // This makes the same call as above, but is linked with the JVM artifact. If Gradle is
    // misconfigured, it'll try to load a different class with the same FQN.
    checkJvmLinkage()

    // This ViewModel will survive configuration changes. It's instantiated
    // by the first call to viewModels(), and that original instance is returned by
    // succeeding calls.
    val model: AppViewModel by viewModels()
    workflowContentView.take(
      lifecycle = lifecycle,
      renderings = model.renderings
        .map { it.withEnvironment(viewEnvironment) }
    )
  }

  @OptIn(WorkflowExperimentalRuntime::class)
  class AppViewModel(savedState: SavedStateHandle) : ViewModel() {
    val renderings: StateFlow<AppRendering> by lazy {
      renderWorkflowIn(
        workflow = AppWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig(),
      )
    }
  }
}

fun <O, R> renderWorkflowInWrapper(
  workflow: Workflow<Unit, O, R>,
  scope: CoroutineScope,
  runtimeConfig: RuntimeConfig,
): StateFlow<R> = renderWorkflowIn(
  workflow = workflow,
  scope = scope,
  runtimeConfig = runtimeConfig,
)
