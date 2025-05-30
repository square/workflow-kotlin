package workflow.tutorial

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.Flow

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // We use an AndroidX ViewModel and its CoroutineScope to start and host
    // an instance of the workflow runtime that runs the WelcomeWorkflow.
    // This ensures that our runtime will survive as new `Activity` instances
    // are created for configuration changes.
    val model: TutorialViewModel by viewModels()

    setContentView(
      WorkflowLayout(this).apply {
        take(lifecycle, model.renderings)
      }
    )
  }

  class TutorialViewModel(savedState: SavedStateHandle) : ViewModel() {

    // We opt in to WorkflowExperimentalRuntime in order turn on all the
    // optimizations controlled by the runtimeConfig.
    //
    // They are in production use at Square, will not be listed as
    // experimental much longer, and will soon be enabled by default.
    // In the meantime it is much easier to use them from the start
    // than to turn them on down the road.
    @OptIn(WorkflowExperimentalRuntime::class)
    val renderings: Flow<Screen> by lazy {
      renderWorkflowIn(
        workflow = WelcomeWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = RuntimeConfigOptions.ALL
      )
    }
  }
}
