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
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

private val viewRegistry = ViewRegistry(WelcomeLayoutRunner)

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: TutorialViewModel by viewModels()

    setContentView(
      WorkflowLayout(this).apply {
        start(model.renderings, viewRegistry)
      }
    )

    setContentView(R.layout.welcome_view)
  }
}

class TutorialViewModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<WelcomeScreen> by lazy {
    renderWorkflowIn(
      workflow = WelcomeWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState
    )
  }
}
