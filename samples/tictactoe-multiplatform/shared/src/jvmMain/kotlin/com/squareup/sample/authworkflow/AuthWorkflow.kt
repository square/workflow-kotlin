package com.squareup.sample.authworkflow

import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthResult.Canceled
import com.squareup.sample.authworkflow.AuthService.AuthRequest
import com.squareup.sample.authworkflow.AuthService.AuthResponse
import com.squareup.sample.authworkflow.AuthService.SecondFactorRequest
import com.squareup.sample.authworkflow.AuthState.Authorizing
import com.squareup.sample.authworkflow.AuthState.AuthorizingSecondFactor
import com.squareup.sample.authworkflow.AuthState.LoginPrompt
import com.squareup.sample.authworkflow.AuthState.SecondFactorPrompt
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen

/**
 * We define this otherwise redundant typealias to keep composite workflows
 * that build on [AuthWorkflow] decoupled from it, for ease of testing.
 */
@OptIn(WorkflowUiExperimentalApi::class)
typealias AuthWorkflow = Workflow<Unit, AuthResult, BackStackScreen<Any>>

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
class RealAuthWorkflow(private val authService: AuthService) : AuthWorkflow,
    StatefulWorkflow<Unit, AuthState, AuthResult, BackStackScreen<Any>>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): AuthState = LoginPrompt()

  override fun render(
    renderProps: Unit,
    renderState: AuthState,
    context: RenderContext
  ): BackStackScreen<Any> = when (renderState) {
    is LoginPrompt -> {
      BackStackScreen(
          LoginScreen(
              renderState.errorMessage,
              onLogin = context.eventHandler { email, password ->
                state = when {
                  email.isValidEmail -> Authorizing(email, password)
                  else -> LoginPrompt(email.emailValidationErrorMessage)
                }
              },
              onCancel = context.eventHandler { setOutput(Canceled) }
          )
      )
    }

    is Authorizing -> {
      context
        .runningWorker(
          Worker.from {
            authService.login(AuthRequest(renderState.email, renderState.password))
          }
        ) {
          handleAuthResponse(it)
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
              onSubmit = context.eventHandler { secondFactor ->
                (state as? SecondFactorPrompt)?.let { oldState ->
                  state = AuthorizingSecondFactor(oldState.tempToken, secondFactor)
                }
              },
              onCancel = context.eventHandler { state = LoginPrompt() }
          )
      )
    }

    is AuthorizingSecondFactor -> {
      val request = SecondFactorRequest(renderState.tempToken, renderState.secondFactor)
      context.runningWorker(Worker.from { authService.secondFactor(request) }) {
        handleSecondFactorResponse(renderState.tempToken, it)
      }

      BackStackScreen(
          LoginScreen(),
          SecondFactorScreen(),
          AuthorizingScreen("Submitting one time token…")
      )
    }
  }

  private fun handleAuthResponse(response: AuthResponse) = action {
    when {
      response.isLoginFailure -> state = LoginPrompt(response.errorMessage)
      response.twoFactorRequired -> state = SecondFactorPrompt(response.token)
      else -> setOutput(Authorized(response.token))
    }
  }

  private fun handleSecondFactorResponse(tempToken: String, response: AuthResponse) = action {
    when {
      response.isSecondFactorFailure ->
        state = SecondFactorPrompt(tempToken, response.errorMessage)
      else -> setOutput(Authorized(response.token))
    }
  }

  /**
   * It'd be silly to restore an in progress login session, so saves nothing.
   */
  override fun snapshotState(state: AuthState): Snapshot? = null
}

private val AuthResponse.isLoginFailure: Boolean
  get() = token.isEmpty() && errorMessage.isNotEmpty()

private val AuthResponse.isSecondFactorFailure: Boolean
  get() = token.isNotEmpty() && errorMessage.isNotEmpty()

private val String.isValidEmail: Boolean
  get() = emailValidationErrorMessage.isBlank()

private val String.emailValidationErrorMessage: String
  get() = if (indexOf('@') < 0) "Invalid address" else ""
