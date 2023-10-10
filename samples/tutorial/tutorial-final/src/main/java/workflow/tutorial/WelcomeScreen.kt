package workflow.tutorial

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.views.databinding.WelcomeViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
data class WelcomeScreen(
  /** The current name that has been entered. */
  val username: TextController,
  /** Callback when the login button is tapped. */
  val onLoginTapped: () -> Unit
) : AndroidScreen<WelcomeScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(WelcomeViewBinding::inflate, ::WelcomeScreenRunner)
}
