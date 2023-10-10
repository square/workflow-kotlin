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
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.navigation.reportNavigation
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.Flow

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Use an AndroidX ViewModel to start and host an instance of the workflow runtime that runs
    // the WelcomeWorkflow and sets the activity's content view using our view factories.
    // By using a ViewModel we ensure that our runtime will survive as new Activity instances
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
    // experimental much longer, and it is much easier to use them from
    // the start than to turn them on down the road.
    @OptIn(WorkflowExperimentalRuntime::class)
    val renderings: Flow<Screen> by lazy {
      renderWorkflowIn(
        workflow = RootNavigationWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState,
        runtimeConfig = RuntimeConfigOptions.ALL
      ).reportNavigation {
        Log.i("Tutorial", "Navigate: ${Compatible.keyFor(it)}")
      }
    }
  }
}
