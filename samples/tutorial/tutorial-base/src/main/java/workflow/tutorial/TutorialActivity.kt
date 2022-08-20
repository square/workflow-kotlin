@file:OptIn(WorkflowUiExperimentalApi::class, WorkflowUiExperimentalApi::class)

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
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow
import workflow.tutorial.root.RootWorkflow
import workflow.tutorial.todolist.ToDoListLayoutRunner
import workflow.tutorial.todolist.ToDoListWorkflow

private val viewRegistry = ViewRegistry(
  BackStackContainer,
  WelcomeLayoutRunner,
  ToDoListLayoutRunner)

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: TutorialViewModel by viewModels()

    setContentView(
      WorkflowLayout(this).apply {
        start(model.renderings, viewRegistry)
      }
    )

    // setContentView(R.layout.welcome_view)
  }
}

class TutorialViewModel(savedState: SavedStateHandle) : ViewModel() {
  val renderings: StateFlow<Any> by lazy {
    // renderWorkflowIn(
    //   workflow = WelcomeWorkflow,
    //   scope = viewModelScope,
    //   savedStateHandle = savedState
    // )

    renderWorkflowIn(
      workflow = RootWorkflow,
      scope = viewModelScope,
      savedStateHandle = savedState
    )

  }
}
