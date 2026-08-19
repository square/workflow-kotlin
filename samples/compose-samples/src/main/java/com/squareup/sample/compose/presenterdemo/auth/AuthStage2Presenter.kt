package com.squareup.sample.compose.presenterdemo.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.ViewModel
import com.squareup.workflow1.ui.compose.ComposeScreen
import kotlinx.coroutines.launch

@Composable
internal fun AuthStage2Presenter(
  authService: AuthService,
  onBack: () -> Unit
) {
  var pin by remember { mutableStateOf("") }
  var showError by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Presenter {
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
      onBack = onBack,
    )
  }
}

data class AuthStage2ViewModel(
  val pin: String,
  val loading: Boolean,
  val onPinChanged: (String) -> Unit,
  val onSubmit: () -> Unit,
  val onBack: () -> Unit,
) : ViewModel, ComposeScreen {

  @Composable override fun Content() {
    // You get the idea…
  }
}
