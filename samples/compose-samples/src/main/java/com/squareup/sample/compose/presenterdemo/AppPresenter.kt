package com.squareup.sample.compose.presenterdemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.squareup.sample.compose.presenterdemo.auth.AuthFlowPresenter
import com.squareup.sample.compose.presenterdemo.auth.AuthService
import com.squareup.sample.compose.presenterdemo.backstack.BackStackPresenter
import com.squareup.sample.compose.presenterdemo.backstack.BackStackScreen

@Composable
fun AppPresenter(
  authService: AuthService,
  appletPresenter: AppletPresenter,
) {
  val isAuthorized by authService.isAuthorized.collectAsState()

  BackStackPresenter(
    // User can't navigate back.
    onBack = null
  ) {
    if (!isAuthorized) {
      AuthFlowPresenter(authService)
    } else {
      ModalHostPresenter(
        // Display all modals in a backstack per layer.
        modalPresenter = { _, children -> BackStackScreen(children, onBack = null) }
      ) {
        ModalPresenter(ModalLayer.Sheet) {
        }
        appletPresenter()
      }
    }
  }
}
