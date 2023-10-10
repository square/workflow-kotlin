package workflow.tutorial

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

@OptIn(WorkflowUiExperimentalApi::class)
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
    val renderings: StateFlow<Screen> by lazy {
      renderWorkflowIn(
        workflow = RootNavigationWorkflow,
        scope = viewModelScope,
        savedStateHandle = savedState
      )
    }
  }
}
