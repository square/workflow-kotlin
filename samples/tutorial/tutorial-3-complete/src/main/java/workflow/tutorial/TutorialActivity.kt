@file:OptIn(WorkflowUiExperimentalApi::class)

package workflow.tutorial

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

private val viewRegistry = ViewRegistry(
    WelcomeLayoutRunner,
    TodoListLayoutRunner,
    TodoEditLayoutRunner
)

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Use an AndroidX ViewModel to start and host an instance of the workflow runtime that runs
    // the WelcomeWorkflow and sets the activity's content view using our view factories.
    val model: TutorialViewModel by viewModels()

    setContentView(
      WorkflowLayout(this).apply { start(model.renderings, viewRegistry) }
    )
  }
}

class TutorialViewModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<Any> by lazy {
    renderWorkflowIn(
      workflow = RootWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState
    )
  }
}
