package workflow.tutorial

import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.setTextChangedListener
import com.squareup.workflow1.ui.updateText
import workflow.tutorial.views.databinding.WelcomeViewBinding

@OptIn(WorkflowUiExperimentalApi::class)
class WelcomeLayoutRunner(
  private val welcomeBinding: WelcomeViewBinding
) : LayoutRunner<WelcomeScreen> {

  override fun showRendering(
    rendering: WelcomeScreen,
    viewEnvironment: ViewEnvironment
  ) {
    welcomeBinding.username.updateText(rendering.userName)
    welcomeBinding.username.setTextChangedListener { rendering.onUsernameChanged(it.toString()) }
    welcomeBinding.login.setOnClickListener { rendering.onLoginTapped() }
  }

  companion object : ViewFactory<WelcomeScreen> by bind(
    WelcomeViewBinding::inflate, ::WelcomeLayoutRunner
  )
}
