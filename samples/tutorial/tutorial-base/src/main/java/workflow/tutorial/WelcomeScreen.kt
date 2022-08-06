package workflow.tutorial

data class WelcomeScreen(
  val userName: String,
  val onUsernameChanged: (String) -> Unit,
  val onLoginTapped: () -> Unit
)

