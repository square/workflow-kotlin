package workflow.tutorial

import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.control
import workflow.tutorial.views.databinding.WelcomeViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
class WelcomeScreenRunner(
  private val welcomeBinding: WelcomeViewBinding
) : ScreenViewRunner<WelcomeScreen> {

  override fun showRendering(
    rendering: WelcomeScreen,
    environment: ViewEnvironment
  ) {
    // TextController is a helper provided by the workflow library that takes
    // care of the complexity of correctly interacting with EditTexts in a declarative manner.
    rendering.username.control(welcomeBinding.username)
    welcomeBinding.login.setOnClickListener { rendering.onLoginTapped() }
  }
}
