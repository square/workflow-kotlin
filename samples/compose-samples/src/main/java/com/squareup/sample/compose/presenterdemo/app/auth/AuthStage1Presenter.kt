package com.squareup.sample.compose.presenterdemo.app.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.squareup.sample.compose.presenterdemo.app.DialogOverlaySlot
import com.squareup.sample.compose.presenterdemo.app.auth.AuthService.Stage1AuthResult
import com.squareup.sample.compose.presenterdemo.structurednav.overlay
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.PresenterModifier
import com.squareup.workflow1.ui.compose.ComposeScreen
import kotlinx.coroutines.launch

@Composable
internal fun AuthStage1Presenter(
  authService: AuthService,
  onStage2Required: () -> Unit,
) {
  // This presenter uses an unconventional shape that doesn't exist in the workflow world:
  // its view model is an interface and the presenter implements it with a concrete type that
  // contains snapshot state-backed properties, and mutates the individual properties directly.
  // This allows even finer-grained view invalidations.

  val viewModel = remember { AuthStage1ViewModelImpl() }
  val scope = rememberCoroutineScope()

  viewModel.onLoginClicked = {
    viewModel.loading = true
    scope.launch {
      when (authService.authenticateStage1(viewModel.username, viewModel.password)) {
        Stage1AuthResult.Succeeded -> {
          // Clear the error status while the screen is animated out.
          viewModel.clearError()
        }

        Stage1AuthResult.Failed -> {
          viewModel.setError()
        }

        Stage1AuthResult.NeedsStage2 -> {
          viewModel.clearError()
          onStage2Required()
        }
      }
      viewModel.loading = false
    }
  }

  // TODO it's bad form to emit two nodes from a single composable. What's a good container for
  //  these? Something that just forwards all slots? Or an overlay-specific thing that has a custom
  //  modifier that tells the container which overlay slot to redirect to?

  // Body
  Presenter(viewModel)

  if (viewModel.showingForgotPasswordDialog) {
    // Overlay
    ForgotPasswordPresenter(
      modifier = PresenterModifier
        .overlay(
          DialogOverlaySlot,
          onDismissRequest = { viewModel.showingForgotPasswordDialog = false }
        )
    )
  }
}

interface AuthStage1ViewModel : ComposeScreen {
  var username: String
  var password: String
  val showError: Boolean
  val loginButtonEnabled: Boolean
  val loading: Boolean

  val onForgotPassword: () -> Unit
  val onLoginClicked: () -> Unit

  @Composable
  override fun Content() {
    Column {
      OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        isError = showError,
      )
      OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        isError = showError,
      )

      Button(
        onClick = onLoginClicked,
        enabled = loginButtonEnabled,
      ) {
        Text("Login")
      }
    }
  }
}

private class AuthStage1ViewModelImpl : AuthStage1ViewModel {

  override var username: String by mutableStateOf("")
  override var password: String by mutableStateOf("")

  override var loading: Boolean by mutableStateOf(false)

  private var errorUsername by mutableStateOf("")
  private var errorPassword by mutableStateOf("")

  override val showError: Boolean
    get() = username.isNotEmpty() && username == errorUsername &&
      password.isNotEmpty() && password == errorPassword

  override val loginButtonEnabled: Boolean
    get() = username.isNotEmpty() && password.isNotEmpty()

  var showingForgotPasswordDialog by mutableStateOf(false)

  override val onForgotPassword: () -> Unit = { showingForgotPasswordDialog = true }
  override var onLoginClicked: () -> Unit by mutableStateOf({})

  fun setError() {
    errorUsername = username
    errorPassword = password
  }

  fun clearError() {
    errorUsername = ""
    errorPassword = ""
  }
}
