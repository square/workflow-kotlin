package workflow.tutorial

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.control
import workflow.tutorial.views.databinding.WelcomeViewBinding

data class WelcomeScreen(
  /** The current name that has been entered. */
  val username: TextController,
  /** Callback when the login button is tapped. */
  val onLogInPressed: () -> Unit
) : AndroidScreen<WelcomeScreen> {

  override val viewFactory =
    ScreenViewFactory.fromViewBinding(WelcomeViewBinding::inflate, ::WelcomeScreenRunner)
}

private class WelcomeScreenRunner(
  private val viewBinding: WelcomeViewBinding
) : ScreenViewRunner<WelcomeScreen> {

  override fun showRendering(
    rendering: WelcomeScreen,
    environment: ViewEnvironment
  ) {
    // TextController is a helper provided by the workflow library that takes
    // care of the complexity of correctly interacting with EditTexts in a declarative manner.
    rendering.username.control(viewBinding.username)
    viewBinding.login.setOnClickListener { rendering.onLogInPressed() }
  }
}
