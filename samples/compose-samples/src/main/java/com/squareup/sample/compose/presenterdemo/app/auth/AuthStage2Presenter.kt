package com.squareup.sample.compose.presenterdemo.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.PresenterModifier
import com.squareup.workflow1.ui.compose.ComposeScreen
import kotlinx.coroutines.launch

@Composable
internal fun AuthStage2Presenter(
  authService: AuthService,
  modifier: PresenterModifier = PresenterModifier,
) {
  // This presenter is implemented with a more conventional workflow pattern: It constructs a new
  // view model on every invalidation, reading from multiple snapshot state objects and aggregating
  // them into a single value type.

  var pin by remember { mutableStateOf("") }
  var showError by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Presenter(modifier = modifier) {
    AuthStage2ViewModel(
      pin = pin,
      loading = loading,
      onPinChanged = {
        pin = it
        showError = false
      },
      onSubmit = {
        loading = true
        scope.launch {
          if (!authService.authenticateStage2(pin)) {
            showError = true
            pin = ""
          }
          loading = false
        }
      },
    )
  }
}

data class AuthStage2ViewModel(
  val pin: String,
  val loading: Boolean,
  val onPinChanged: (String) -> Unit,
  val onSubmit: () -> Unit,
) : ComposeScreen {

  @Composable override fun Content() {
    // You get the idea…
  }
}
