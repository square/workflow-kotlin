@file:OptIn(WorkflowUiExperimentalApi::class)

package workflow.tutorial

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowRunner.Config
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.setContentWorkflow

private val viewRegistry = ViewRegistry(
    BackStackContainer,
    WelcomeLayoutRunner,
    TodoListLayoutRunner,
    TodoEditLayoutRunner
)

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Use an AndroidX ViewModel to start and host an instance of the workflow runtime that runs
    // the WelcomeWorkflow and sets the activity's content view using our view factories.
    setContentWorkflow(viewRegistry) {
      Config(RootWorkflow, Unit)
    }
  }
}
