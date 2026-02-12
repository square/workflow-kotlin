@file:OptIn(WorkflowExperimentalRuntime::class)

package workflow.tutorial

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.android.renderWorkflowIn
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.reportNavigation
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.Flow

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // We use an AndroidX ViewModel and its CoroutineScope to start and host
    // an instance of the workflow runtime that runs the WelcomeWorkflow.
    // This ensures that our runtime will survive as new `Activity` instances
    // are created for configuration changes.
    val model: TutorialViewModel by viewModels()

    workflowContentView.take(lifecycle, model.renderings)
  }

  class TutorialViewModel(savedState: SavedStateHandle) : ViewModel() {
    val renderings: Flow<Screen> by lazy {
      renderWorkflowIn(
        workflow = RootNavigationWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = RuntimeConfigOptions.ALL
      ).reportNavigation {
        Log.i("navigate", it.toString())
      }
    }
  }
}
