package com.squareup.sample.authworkflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rxjava2.subscribeAsState
import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthResult.Canceled
import com.squareup.sample.authworkflow.AuthService.AuthRequest
import com.squareup.sample.authworkflow.AuthService.AuthResponse
import com.squareup.sample.authworkflow.AuthService.SecondFactorRequest
import com.squareup.sample.authworkflow.AuthState.Authorizing
import com.squareup.sample.authworkflow.AuthState.AuthorizingSecondFactor
import com.squareup.sample.authworkflow.AuthState.LoginPrompt
import com.squareup.sample.authworkflow.AuthState.SecondFactorPrompt
import com.squareup.workflow.compose.ComposeWorkflow
import com.squareup.workflow.compose.StatefulComposeWorkflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import io.reactivex.Single

/**
 * We define this otherwise redundant typealias to keep composite workflows
 * that build on [AuthWorkflow] decoupled from it, for ease of testing.
 */
@OptIn(WorkflowUiExperimentalApi::class)
typealias AuthComposeWorkflow = ComposeWorkflow<Unit, AuthResult, BackStackScreen<Any>>

/**
 * Runs a set of login screens and pretends to produce an auth token,
 * via a pretend [authService].
 *
 * Demonstrates both client side validation (email format, must include "@")
 * and server side validation (password is "password").
 *
 * Includes a 2fa path for email addresses that include the string "2fa".
 * Token is "1234".
 */
@OptIn(WorkflowUiExperimentalApi::class)
class RealAuthComposeWorkflow(private val authService: AuthService) : AuthComposeWorkflow,
  StatefulComposeWorkflow<Unit, AuthState, AuthResult, BackStackScreen<Any>>() {

  override fun initialState(
    props: Unit,
  ): AuthState = LoginPrompt()

  @Composable override fun render(
    renderProps: Unit,
    renderState: AuthState,
    context: RenderContext
  ): BackStackScreen<Any> = when (renderState) {
    is LoginPrompt -> {
      BackStackScreen(
        LoginScreen(
          renderState.errorMessage,
          onLogin = { email, password ->
            context.state = when {
              email.isValidEmail -> Authorizing(email, password)
              else -> LoginPrompt(email.emailValidationErrorMessage)
            }
          },
          onCancel = { context.setOutput(Canceled) }
        )
      )
    }

    is Authorizing -> {
      authService.login(AuthRequest(renderState.email, renderState.password)).onResult {
        context.handleAuthResponse(it)
      }

      BackStackScreen(
        LoginScreen(),
        AuthorizingScreen("Logging in…")
      )
    }

    is SecondFactorPrompt -> {
      BackStackScreen(
        LoginScreen(),
        SecondFactorScreen(
          renderState.errorMessage,
          onSubmit = { secondFactor ->
            (renderState as? SecondFactorPrompt)?.let { oldState ->
              context.state = AuthorizingSecondFactor(oldState.tempToken, secondFactor)
            }
          },
          onCancel = { context.state = LoginPrompt() }
        )
      )
    }

    is AuthorizingSecondFactor -> {
      val request = SecondFactorRequest(renderState.tempToken, renderState.secondFactor)
      authService.secondFactor(request).onResult {
        context.handleSecondFactorResponse(renderState.tempToken, it)
      }

      BackStackScreen(
        LoginScreen(),
        SecondFactorScreen(),
        AuthorizingScreen("Submitting one time token…")
      )
    }
  }

  private fun RenderContext.handleAuthResponse(response: AuthResponse) {
    when {
      response.isLoginFailure -> state = LoginPrompt(response.errorMessage)
      response.twoFactorRequired -> state = SecondFactorPrompt(response.token)
      else -> setOutput(Authorized(response.token))
    }
  }

  private fun RenderContext.handleSecondFactorResponse(
    tempToken: String,
    response: AuthResponse
  ) {
    when {
      response.isSecondFactorFailure ->
        state = SecondFactorPrompt(tempToken, response.errorMessage)
      else -> setOutput(Authorized(response.token))
    }
  }
}

private val AuthResponse.isLoginFailure: Boolean
  get() = token.isEmpty() && errorMessage.isNotEmpty()

private val AuthResponse.isSecondFactorFailure: Boolean
  get() = token.isNotEmpty() && errorMessage.isNotEmpty()

private val String.isValidEmail: Boolean
  get() = emailValidationErrorMessage.isBlank()

private val String.emailValidationErrorMessage: String
  get() = if (indexOf('@') < 0) "Invalid address" else ""

@Composable
fun <T> Single<T>.onResult(block: (T) -> Unit) = subscribeAsState(null).value?.also(block)
