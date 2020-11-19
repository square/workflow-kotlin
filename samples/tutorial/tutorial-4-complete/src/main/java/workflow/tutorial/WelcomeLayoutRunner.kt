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
    // updateText and setTextChangedListener are helpers provided by the workflow library that take
    // care of the complexity of correctly interacting with EditTexts in a declarative manner.
    welcomeBinding.username.updateText(rendering.username)
    welcomeBinding.username.setTextChangedListener {
      rendering.onUsernameChanged(it.toString())
    }
    welcomeBinding.login.setOnClickListener { rendering.onLoginTapped() }
  }

  /**
   * Define a [ViewFactory] that will inflate an instance of [WelcomeViewBinding] and an instance
   * of [WelcomeLayoutRunner] when asked, then wire them up so that [showRendering] will be called
   * whenever the workflow emits a new [WelcomeScreen].
   */
  companion object : ViewFactory<WelcomeScreen> by bind(
    WelcomeViewBinding::inflate, ::WelcomeLayoutRunner
  )
}
