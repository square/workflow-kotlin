package workflow.tutorial

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import workflow.tutorial.views.databinding.WelcomeViewBinding

/**
 * @param promptText error text or other message to show the users
 * @param onLogInTapped Log In button event handler
 */
data class WelcomeScreen(
  val promptText: String,
  val onLogInTapped: (String) -> Unit
) : AndroidScreen<WelcomeScreen> {

  override val viewFactory =
    ScreenViewFactory.fromViewBinding(WelcomeViewBinding::inflate, ::welcomeScreenRunner)
}

private fun welcomeScreenRunner(
  viewBinding: WelcomeViewBinding
) = ScreenViewRunner { screen: WelcomeScreen, _ ->
  viewBinding.prompt.text = screen.promptText
  viewBinding.logIn.setOnClickListener {
    screen.onLogInTapped(viewBinding.username.text.toString())
  }
}
